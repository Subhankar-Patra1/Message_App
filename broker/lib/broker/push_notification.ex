defmodule Broker.PushNotification do
  @moduledoc """
  Firebase Cloud Messaging (FCM) v1 HTTP API integration.

  Uses the Firebase service account JSON to obtain an OAuth2 access token,
  then sends push notifications via the FCM v1 API over HTTP/2.

  Access tokens are cached in Valkey with a 55-minute TTL (they expire at 60 min).
  """
  require Logger
  alias Broker.Repo
  alias Broker.Accounts.Device
  import Ecto.Query

  @fcm_scope "https://www.googleapis.com/auth/firebase.messaging"
  @token_url "https://oauth2.googleapis.com/token"
  @token_cache_key "fcm:access_token"
  # 55 minutes (tokens are valid for 60 min)
  @token_ttl_seconds 3300

  # --- Public API ---

  @doc """
  Send a push notification to a user asynchronously via Task.Supervisor.
  This will not block the caller. Failures are logged but do not crash the caller.
  """
  def send_async(user_id, title, body, data \\ %{}) do
    Task.Supervisor.start_child(Broker.PushTaskSupervisor, fn ->
      send_to_user(user_id, title, body, data)
    end)
  end

  @doc """
  Send a push notification to a user synchronously.
  Looks up the user's active device push token and sends via FCM.
  """
  def send_to_user(user_id, title, body, data \\ %{}) do
    case get_push_token(user_id) do
      {:ok, push_token} ->
        send_fcm(push_token, title, body, data)

      {:error, :no_push_token} ->
        Logger.debug("No push token for user #{user_id}, skipping push notification")
        {:error, :no_push_token}
    end
  end

  # --- FCM HTTP/2 API ---

  defp send_fcm(push_token, title, body, data) do
    case get_access_token() do
      {:ok, access_token} ->
        project_id = firebase_project_id()
        url = "https://fcm.googleapis.com/v1/projects/#{project_id}/messages:send"

        message = %{
          "message" => %{
            "token" => push_token,
            "notification" => %{
              "title" => title,
              "body" => body
            },
            "data" => Map.new(data, fn {k, v} -> {to_string(k), to_string(v)} end),
            "android" => %{
              "priority" => "high",
              "notification" => %{
                "channel_id" => "messages",
                "sound" => "default"
              }
            }
          }
        }

        headers = [
          {"Authorization", "Bearer #{access_token}"},
          {"Content-Type", "application/json"}
        ]

        request = Finch.build(:post, url, headers, Jason.encode!(message))

        case Finch.request(request, Broker.Finch) do
          {:ok, %Finch.Response{status: 200}} ->
            Logger.info(
              "FCM push sent successfully to token #{String.slice(push_token, 0..15)}..."
            )

            :ok

          {:ok, %Finch.Response{status: 404, body: resp_body}} ->
            # Token is invalid/unregistered — mark it as stale
            Logger.warning("FCM token unregistered, clearing push_token: #{resp_body}")
            clear_push_token_by_value(push_token)
            {:error, :token_unregistered}

          {:ok, %Finch.Response{status: 429}} ->
            Logger.warning("FCM rate limit hit, will retry later")
            {:error, :rate_limited}

          {:ok, %Finch.Response{status: status, body: resp_body}} ->
            Logger.error("FCM push failed with status #{status}: #{resp_body}")
            {:error, {:fcm_error, status}}

          {:error, reason} ->
            Logger.error("FCM HTTP request failed: #{inspect(reason)}")
            {:error, reason}
        end

      {:error, reason} ->
        Logger.error("Failed to get FCM access token: #{inspect(reason)}")
        {:error, reason}
    end
  end

  # --- OAuth2 Access Token (via Service Account JWT) ---

  defp get_access_token do
    # Check Valkey cache first
    case Redix.command(:redix, ["GET", @token_cache_key]) do
      {:ok, token} when is_binary(token) ->
        {:ok, token}

      _ ->
        # Generate a new token from the service account
        fetch_and_cache_access_token()
    end
  end

  defp fetch_and_cache_access_token do
    sa = load_service_account()
    now = System.system_time(:second)

    # Build the JWT assertion
    jwt_header = %{"alg" => "RS256", "typ" => "JWT"}

    jwt_claims = %{
      "iss" => sa["client_email"],
      "scope" => @fcm_scope,
      "aud" => @token_url,
      "iat" => now,
      "exp" => now + 3600
    }

    # Sign with the service account private key
    jwk = JOSE.JWK.from_pem(sa["private_key"])
    {_, signed_jwt} = JOSE.JWS.compact(JOSE.JWT.sign(jwk, jwt_header, jwt_claims))

    # Exchange JWT assertion for access token
    body =
      URI.encode_query(%{
        "grant_type" => "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion" => signed_jwt
      })

    headers = [{"Content-Type", "application/x-www-form-urlencoded"}]
    request = Finch.build(:post, @token_url, headers, body)

    case Finch.request(request, Broker.Finch) do
      {:ok, %Finch.Response{status: 200, body: resp_body}} ->
        %{"access_token" => access_token} = Jason.decode!(resp_body)
        # Cache in Valkey for 55 minutes
        Redix.command(:redix, [
          "SETEX",
          @token_cache_key,
          to_string(@token_ttl_seconds),
          access_token
        ])

        {:ok, access_token}

      {:ok, %Finch.Response{status: status, body: resp_body}} ->
        Logger.error("Google OAuth2 token exchange failed: status=#{status} body=#{resp_body}")
        {:error, {:oauth_error, status}}

      {:error, reason} ->
        {:error, reason}
    end
  end

  # --- Database Helpers ---

  defp get_push_token(user_id) do
    device =
      Repo.one(
        from d in Device,
          where: d.user_id == ^user_id and d.is_active == true and not is_nil(d.push_token),
          order_by: [desc: d.updated_at],
          limit: 1,
          select: d.push_token
      )

    if device, do: {:ok, device}, else: {:error, :no_push_token}
  end

  defp clear_push_token_by_value(push_token) do
    from(d in Device, where: d.push_token == ^push_token)
    |> Repo.update_all(set: [push_token: nil])
  end

  # --- Config ---

  defp firebase_project_id do
    Application.get_env(:broker, :firebase_project_id, "messenger-bd0f5")
  end

  defp load_service_account do
    path =
      Application.get_env(
        :broker,
        :firebase_service_account_path,
        Path.join(
          :code.priv_dir(:broker),
          "messenger-bd0f5-firebase-adminsdk-fbsvc-f321719fb6.json"
        )
      )

    path |> File.read!() |> Jason.decode!()
  end
end
