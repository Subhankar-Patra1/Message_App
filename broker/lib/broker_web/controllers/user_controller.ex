defmodule BrokerWeb.UserController do
  use BrokerWeb, :controller
  alias Broker.Accounts.User
  alias Broker.Repo
  import Ecto.Query

  # Build a full URL for avatar paths stored as "/uploads/xxx.jpg"
  defp full_avatar_url(_conn, nil), do: nil
  defp full_avatar_url(conn, "/" <> _ = path) do
    %{scheme: scheme, host: host, port: port} = conn
    base = "#{scheme || "http"}://#{host}#{if port in [80, 443, nil], do: "", else: ":#{port}"}"
    "#{base}#{path}"
  end
  defp full_avatar_url(_conn, url), do: url

  # GET /api/v1/account/check_username?username=...
  # Public endpoint — no auth required
  def check_username(conn, %{"username" => username}) when is_binary(username) do
    taken = Repo.exists?(from u in User, where: u.username == ^username)
    json(conn, %{available: !taken})
  end

  def check_username(conn, _params) do
    conn
    |> put_status(:bad_request)
    |> json(%{error: "username parameter is required"})
  end

  # PUT /api/v1/account/profile
  # Accepts: first_name, last_name, avatar (multipart file)
  def update_profile(conn, params) do
    user_id = conn.assigns.current_user_id
    user = Repo.get!(User, user_id)

    # Handle file upload if present
    avatar_url =
      if upload = params["avatar"] do
        # In a real production app, you'd upload this to S3 or similar.
        # For this prototype, we'll store it locally in priv/static/uploads
        # and serve it statically.
        upload_dir = Application.app_dir(:broker, "priv/static/uploads")
        File.mkdir_p!(upload_dir)

        ext = Path.extname(upload.filename)
        filename = "#{Ecto.UUID.generate()}#{ext}"
        dest = Path.join(upload_dir, filename)

        File.cp!(upload.path, dest)
        "/uploads/#{filename}"
      else
        user.avatar_url
      end

    attrs = %{
      first_name: params["first_name"] || user.first_name,
      last_name: params["last_name"] || user.last_name,
      avatar_url: avatar_url
    }

    changeset = User.changeset(user, attrs)

    case Repo.update(changeset) do
      {:ok, updated_user} ->
        json(conn, %{
          success: true,
          user: %{
            id: updated_user.id,
            first_name: updated_user.first_name,
            last_name: updated_user.last_name,
            avatar_url: full_avatar_url(conn, updated_user.avatar_url)
          }
        })

      {:error, changeset} ->
        errors = Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
          Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
            opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
          end)
        end)

        conn
        |> put_status(:unprocessable_entity)
        |> json(%{success: false, errors: errors})
    end
  end

  # PUT /api/v1/account/username
  # Accepts: username, pin (optional)
  def set_username(conn, params) do
    user_id = conn.assigns.current_user_id
    user = Repo.get!(User, user_id)

    # If pin is provided, hash it using Pbkdf2
    username_pin_hash =
      case params["pin"] do
        pin when is_binary(pin) and byte_size(pin) == 4 ->
          Pbkdf2.hash_pwd_salt(pin)

        _ ->
          nil
      end

    attrs = %{
      username: params["username"],
      username_pin_hash: username_pin_hash
    }

    changeset = User.username_changeset(user, attrs)

    case Repo.update(changeset) do
      {:ok, updated_user} ->
        json(conn, %{
          success: true,
          user: %{
            id: updated_user.id,
            username: updated_user.username,
            has_pin: updated_user.username_pin_hash != nil
          }
        })

      {:error, changeset} ->
        errors = Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
          Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
            opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
          end)
        end)

        conn
        |> put_status(:unprocessable_entity)
        |> json(%{success: false, errors: errors})
    end
  end

  # GET /api/v1/account/profile
  def show_profile(conn, _params) do
    user_id = conn.assigns.current_user_id
    user = Repo.get!(User, user_id)

    json(conn, %{
      user_id: user.id,
      first_name: user.first_name,
      last_name: user.last_name,
      username: user.username,
      avatar_url: full_avatar_url(conn, user.avatar_url),
      email: user.email
    })
  end

  # GET /api/v1/account/status/:user_id
  def get_status(conn, %{"user_id" => user_id}) do
    # Check if user is online via Presence
    # Presence channel format is encrypted_chat:{user_id}
    # But note: The Presence list for a specific topic only returns users connected to *that* topic.
    # To check global online status, we can check if they are in their own topic.
    presences = BrokerWeb.Presence.list("encrypted_chat:#{user_id}")
    is_online = Map.has_key?(presences, user_id)

    last_seen_at =
      if !is_online do
        user = Repo.get(User, user_id)
        if user && user.last_seen_at do
          DateTime.to_iso8601(user.last_seen_at)
        else
          nil
        end
      else
        nil
      end

    json(conn, %{
      user_id: user_id,
      online: is_online,
      last_seen_at: last_seen_at
    })
  end

  # GET /api/v1/account/public_profile/:user_id
  # Returns display name, username, and avatar for any registered user.
  # Used by the client to resolve UUIDs into human-readable names.
  def get_public_profile(conn, %{"user_id" => user_id}) do
    case Repo.get(User, user_id) do
      nil ->
        conn
        |> put_status(:not_found)
        |> json(%{error: "User not found"})

      user ->
        display_name = String.trim("#{user.first_name || ""} #{user.last_name || ""}")
        display_name = if display_name == "", do: user.username, else: display_name

        json(conn, %{
          user_id: user.id,
          display_name: display_name,
          username: user.username,
          avatar_url: full_avatar_url(conn, user.avatar_url)
        })
    end
  end
end
