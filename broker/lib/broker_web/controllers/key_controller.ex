defmodule BrokerWeb.KeyController do
  use BrokerWeb, :controller
  require Logger
  alias Broker.Repo
  alias Broker.Keys.{UploadPayload, PreKey, SignedPreKey}
  alias Broker.Accounts.Device
  alias Broker.Accounts.User
  alias Broker.Audit.KeyRegistration

  action_fallback BrokerWeb.FallbackController

  defp full_avatar_url(_conn, nil), do: nil
  defp full_avatar_url(conn, "/" <> _ = path) do
    %{scheme: scheme, host: host, port: port} = conn
    base = "#{scheme || "http"}://#{host}#{if port in [80, 443, nil], do: "", else: ":#{port}"}"
    "#{base}#{path}"
  end
  defp full_avatar_url(_conn, url), do: url

  # Plugs for scopes and rate limiting
  plug BrokerWeb.Plugs.RequireScope,
       "keys:register" when action in [:register, :rotate_signed_pre_key]

  plug BrokerWeb.Plugs.RequireScope, "pre_keys:fetch" when action in [:fetch_bundle, :fetch_by_username, :fetch_by_identifier]

  plug BrokerWeb.Plugs.RateLimit,
       [action: "register_keys", scale_ms: 300_000, limit: 10]
       when action in [:register, :rotate_signed_pre_key]

  plug BrokerWeb.Plugs.RateLimit,
       [action: "fetch_keys", scale_ms: 60_000, limit: 30] when action in [:fetch_bundle, :fetch_by_username, :fetch_by_identifier]

  def register(conn, params) do
    with :ok <- check_version(params),
         {:ok, idem_key} <- check_idempotency(conn),
         {:ok, payload} <- parse_payload(params),
         {:ok, _result} <- execute_registration_transaction(conn, payload) do
      mark_idempotency_processed(idem_key)

      conn
      |> put_status(:created)
      |> json(%{success: true})
    else
      {:processed, _idem_key} ->
        conn
        |> put_status(:created)
        |> json(%{success: true})

      error ->
        error
    end
  end

  def fetch_bundle(conn, %{"recipient_id" => recipient_id}) do
    user_id = conn.assigns.current_user_id
    {:ok, valid_recipient_id} = cast_recipient_id(recipient_id)

    # TODO: check_cache(valid_recipient_id) when Redis is integrated
    case execute_fetch_transaction(valid_recipient_id, user_id) do
      {:ok, bundle} ->
        cache_bundle(valid_recipient_id, bundle)
        json(conn, bundle)

      {:error, reason} ->
        {:error, reason}
    end
  end

  def fetch_bundle(_conn, _), do: {:error, :missing_field}

  def fetch_by_username(conn, %{"username" => username} = params) do
    user_id = conn.assigns.current_user_id
    
    case Repo.get_by(User, username: username) do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "user_not_found"})

      user ->
        if user.id == user_id do
          conn |> put_status(400) |> json(%{error: "cannot_message_self"})
        else
          # Check PIN if provided
          pin_correct =
            if user.username_pin_hash do
              case params["pin"] do
                pin when is_binary(pin) -> Pbkdf2.verify_pass(pin, user.username_pin_hash)
                _ -> false
              end
            else
              true
            end

          display_name = String.trim("#{user.first_name || ""} #{user.last_name || ""}")
          profile_data = %{
            "user_id" => user.id,
            "first_name" => user.first_name,
            "last_name" => user.last_name,
            "display_name" => if(display_name == "", do: user.username, else: display_name),
            "avatar_url" => full_avatar_url(conn, user.avatar_url),
            "username" => user.username,
            "pin_required" => user.username_pin_hash != nil
          }

          if pin_correct do
            case execute_fetch_transaction(user.id, user_id) do
              {:ok, bundle} ->
                json(conn, Map.merge(bundle, profile_data))

              {:error, reason} ->
                conn |> put_status(500) |> json(%{error: inspect(reason)})
            end
          else
            # If PIN is required but missing/wrong, still return the profile but with a flag
            if params["pin"] != nil do
              conn |> put_status(:unauthorized) |> json(%{error: "invalid_pin"})
            else
              json(conn, profile_data)
            end
          end
        end
    end
  end

  def fetch_by_username(conn, _), do: conn |> put_status(400) |> json(%{error: "missing_username"})

  @doc """
  GET /api/v1/keys/fetch_by_identifier?identifier=...

  Finds a user by phone number or email and returns their pre-key bundle.
  Phone numbers are hashed before lookup. Emails are matched directly.
  """
  def fetch_by_identifier(conn, %{"identifier" => identifier}) do
    user_id = conn.assigns.current_user_id

    is_phone =
      Regex.match?(~r/^\+?[0-9\s\-()]+$/, String.trim(identifier)) and
        String.length(String.replace(identifier, ~r/[^0-9]/, "")) >= 7

    user =
      if is_phone do
        phone_hash = hash_phone(identifier)
        Repo.get_by(User, phone_hash: phone_hash)
      else
        Repo.get_by(User, email: identifier)
      end

    case user do
      nil ->
        conn |> put_status(:not_found) |> json(%{error: "user_not_found"})

      found_user ->
        if found_user.id == user_id do
          conn |> put_status(400) |> json(%{error: "cannot_message_self"})
        else
          case execute_fetch_transaction(found_user.id, user_id) do
            {:ok, bundle} ->
              display_name = String.trim("#{found_user.first_name || ""} #{found_user.last_name || ""}")
              
              bundle_with_id = bundle
                |> Map.put("user_id", found_user.id)
                |> Map.put("first_name", found_user.first_name)
                |> Map.put("last_name", found_user.last_name)
                |> Map.put("display_name", if(display_name == "", do: found_user.username || identifier, else: display_name))
                |> Map.put("avatar_url", full_avatar_url(conn, found_user.avatar_url))
                |> Map.put("username", found_user.username)
              json(conn, bundle_with_id)

            {:error, reason} ->
              conn |> put_status(500) |> json(%{error: inspect(reason)})
          end
        end
    end
  end

  def fetch_by_identifier(conn, _), do: conn |> put_status(400) |> json(%{error: "missing_identifier"})

  defp hash_phone(phone) do
    secret = Application.get_env(:broker, :phone_hash_secret)
    :crypto.mac(:hmac, :sha256, secret, phone) |> Base.encode64()
  end

  # --- Helpers ---

  defp check_version(%{"version" => "1.0"}), do: :ok

  defp check_version(%{"version" => unsupported}) do
    Logger.warning("Unsupported protocol version", version: unsupported)
    {:error, :unsupported_version}
  end

  defp check_version(_), do: {:error, :missing_version}

  defp check_idempotency(conn) do
    case get_req_header(conn, "idempotency-key") do
      [key] ->
        # Redis mock/actual implementation
        # For real: Redix.command(:redix, ["GET", "idem:#{key}"])
        # Mocking for now to avoid requiring running Redis:
        {:ok, key}

      [] ->
        {:ok, nil}
    end
  end

  defp mark_idempotency_processed(nil), do: :ok

  defp mark_idempotency_processed(_key) do
    # For real: Redix.command(:redix, ["SETEX", "idem:#{key}", 86400, "processed"])
    :ok
  end

  # TODO: Add check_cache/1 when Redis is integrated

  defp cache_bundle(_recipient_id, _bundle) do
    # TODO: Redix SETEX cache:bundle:{recipient_id} 300 Jason.encode!(bundle)
    :ok
  end

  defp parse_payload(params) do
    changeset = UploadPayload.changeset(%UploadPayload{}, params)

    if changeset.valid? do
      {:ok, Ecto.Changeset.apply_changes(changeset)}
    else
      {:error, changeset}
    end
  end

  defp execute_registration_transaction(conn, payload) do
    user_id = conn.assigns.current_user_id

    Ecto.Multi.new()
    |> Ecto.Multi.run(:device, fn repo, _changes ->
      # Upsert device based on user_id and device_id
      device_params = %{
        user_id: user_id,
        device_uuid: payload.device_id,
        platform: "android",
        public_identity_key: payload.identity_key.public,
        registration_id: payload.pre_keys |> hd() |> Map.get(:key_id, 0),
        is_active: true
      }

      case repo.get_by(Device, user_id: user_id, device_uuid: payload.device_id) do
        nil ->
          %Device{} |> Device.changeset(device_params) |> repo.insert()

        existing ->
          existing |> Device.changeset(device_params) |> repo.update()
      end
    end)
    |> Ecto.Multi.run(:signed_pre_key, fn repo, %{device: device} ->
      params = %{
        device_id: device.id,
        key_id: payload.signed_pre_key.key_id,
        public_key: payload.signed_pre_key.public,
        signature: payload.signed_pre_key.signature,
        is_active: true
      }

      %SignedPreKey{} |> SignedPreKey.changeset(params) |> repo.insert()
    end)
    |> Ecto.Multi.run(:pre_keys, fn repo, %{device: device} ->
      now = DateTime.utc_now() |> DateTime.truncate(:second)

      entries =
        Enum.map(payload.pre_keys, fn pk ->
          %{
            id: Ecto.UUID.generate(),
            device_id: device.id,
            key_id: pk.key_id,
            public_key: pk.public,
            is_used: false,
            inserted_at: now,
            updated_at: now
          }
        end)

      {count, nil} = repo.insert_all(PreKey, entries, on_conflict: :nothing)
      {:ok, count}
    end)
    |> Ecto.Multi.run(:audit, fn repo, %{device: device, pre_keys: pre_keys_count} ->
      params = %{
        user_id_hash: :crypto.hash(:sha256, user_id),
        device_id_hash: :crypto.hash(:sha256, device.id),
        action: "created",
        pre_keys_count: pre_keys_count
      }

      %KeyRegistration{} |> KeyRegistration.changeset(params) |> repo.insert()
    end)
    |> Repo.transaction(timeout: 5000)
  end

  defp execute_fetch_transaction(recipient_id, _requester_id) do
    Ecto.Multi.new()
    |> Ecto.Multi.run(:device, fn repo, _ ->
      import Ecto.Query

      device =
        repo.one(
          from d in Device,
            where: d.user_id == ^recipient_id and d.is_active == true,
            order_by: [desc: d.last_seen, desc: d.inserted_at],
            limit: 1
        )

      if device, do: {:ok, device}, else: {:error, :not_found}
    end)
    |> Ecto.Multi.run(:signed_pre_key, fn repo, %{device: device} ->
      import Ecto.Query

      spk =
        repo.one(
          from s in SignedPreKey,
            where: s.device_id == ^device.id and s.is_active == true,
            order_by: [desc: s.rotated_at, desc: s.inserted_at],
            limit: 1
        )

      {:ok, spk}
    end)
    |> Ecto.Multi.run(:pre_keys, fn repo, %{device: device} ->
      import Ecto.Query

      pks =
        repo.all(
          from p in PreKey,
            where: p.device_id == ^device.id and p.is_used == false,
            order_by: [asc: p.key_id],
            limit: 10,
            lock: "FOR UPDATE SKIP LOCKED"
        )

      if pks != [] do
        ids = Enum.map(pks, & &1.id)

        repo.update_all(
          from(p in PreKey, where: p.id in ^ids),
          set: [is_used: true]
        )
      end

      {:ok, pks}
    end)
    |> Ecto.Multi.run(:remaining_count, fn repo, %{device: device} ->
      import Ecto.Query

      count =
        repo.aggregate(
          from(p in PreKey, where: p.device_id == ^device.id and p.is_used == false),
          :count,
          :id
        )

      {:ok, count}
    end)
    |> Repo.transaction(timeout: 5000)
    |> case do
      {:ok, %{device: device, signed_pre_key: spk, pre_keys: pks, remaining_count: remaining}} ->
        bundle = %{
          "version" => "1.0",
          "identity_key" => %{
            "public" => Base.url_encode64(device.public_identity_key, padding: false)
          },
          "signed_pre_key" =>
            if spk do
              %{
                "key_id" => spk.key_id,
                "public" => Base.url_encode64(spk.public_key, padding: false),
                "signature" => Base.url_encode64(spk.signature, padding: false)
              }
            else
              nil
            end,
          "pre_keys" =>
            Enum.map(pks, fn pk ->
              %{
                "key_id" => pk.key_id,
                "public" => Base.url_encode64(pk.public_key, padding: false)
              }
            end),
          "device_id" => device.device_uuid,
          "pre_keys_remaining_hint" => remaining
        }

        {:ok, bundle}

      {:error, _step, reason, _changes} ->
        {:error, reason}
    end
  end

  @spec cast_recipient_id(String.t()) :: {:ok, String.t()} | {:error, :invalid_format}
  defp cast_recipient_id(id) do
    case Ecto.UUID.cast(id) do
      {:ok, uuid} ->
        {:ok, uuid}

      :error ->
        # For dev: Hash name into UUID
        {:ok, :crypto.hash(:md5, id) |> Ecto.UUID.cast!()}
    end
  end

  @doc """
  PUT /api/v1/keys/signed_pre_key

  Rotates the signed pre-key for the current device.
  Marks the old active signed pre-key as inactive and inserts the new one.
  """
  def rotate_signed_pre_key(conn, %{
        "key_id" => key_id,
        "public" => public_b64,
        "signature" => signature_b64
      }) do
    user_id = conn.assigns.current_user_id
    device_uuid = conn.assigns.current_device_id

    import Ecto.Query

    device = Repo.get_by(Device, user_id: user_id, device_uuid: device_uuid, is_active: true)

    if device do
      # Decode the base64-encoded binary fields
      with {:ok, public_key} <- Base.url_decode64(public_b64, padding: false),
           {:ok, signature} <- Base.url_decode64(signature_b64, padding: false) do
        Ecto.Multi.new()
        |> Ecto.Multi.run(:deactivate_old, fn repo, _ ->
          {count, _} =
            repo.update_all(
              from(s in SignedPreKey, where: s.device_id == ^device.id and s.is_active == true),
              set: [
                is_active: false,
                rotated_at: DateTime.utc_now() |> DateTime.truncate(:second)
              ]
            )

          {:ok, count}
        end)
        |> Ecto.Multi.run(:insert_new, fn repo, _ ->
          params = %{
            device_id: device.id,
            key_id: key_id,
            public_key: public_key,
            signature: signature,
            is_active: true
          }

          %SignedPreKey{} |> SignedPreKey.changeset(params) |> repo.insert()
        end)
        |> Repo.transaction()
        |> case do
          {:ok, _} ->
            conn |> put_status(:ok) |> json(%{success: true})

          {:error, _step, reason, _} ->
            conn |> put_status(422) |> json(%{error: inspect(reason)})
        end
      else
        _ ->
          conn |> put_status(400) |> json(%{error: "invalid_base64_encoding"})
      end
    else
      conn |> put_status(404) |> json(%{error: "device_not_found"})
    end
  end

  def rotate_signed_pre_key(conn, _),
    do: conn |> put_status(400) |> json(%{error: "missing_fields"})
end
