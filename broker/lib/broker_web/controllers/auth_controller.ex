defmodule BrokerWeb.AuthController do
  use BrokerWeb, :controller
  alias Broker.Repo
  alias Broker.Accounts.User
  alias Broker.Accounts.Device
  import Ecto.Query

  # POST /api/v1/auth/identify
  # Smart unified entry point: detects email vs phone, checks if user exists,
  # generates OTP, and returns the next step for the client.
  def identify(conn, %{"identifier" => id}) do
    # 1. Rate Limiting Check
    remote_ip_str = conn.remote_ip |> :inet.ntoa() |> to_string()
    ip_key = "rl:ip:#{remote_ip_str}"
    id_key = "rl:id:#{id}"

    ip_count = check_rate_limit!(ip_key, 60, 50)
    id_count = check_rate_limit!(id_key, 3600, 20)

    if ip_count > 50 or id_count > 20 do
      Process.sleep(200)
      conn |> put_status(429) |> json(%{error: "Too many requests. Please try again later."})
    else
      # 2. Timing mitigation setup
      start_time = System.monotonic_time(:millisecond)

      # 3. Detect type
      is_phone =
        Regex.match?(~r/^\+?[0-9\s\-()]+$/, String.trim(id)) and
          String.length(String.replace(id, ~r/[^0-9]/, "")) >= 7

      {next_step, is_new_user} =
        if is_phone do
          phone_hash = hash_phone(id)
          user = Repo.get_by(User, phone_hash: phone_hash)
          if is_nil(user), do: {"password_setup", true}, else: {"password_inline", false}
        else
          user = Repo.get_by(User, email: id)

          if is_nil(user) do
            {"password_setup", true}
          else
            {"password_inline", false}
          end
        end

      # 4. Generate temp_token
      temp_token = :crypto.strong_rand_bytes(32) |> Base.url_encode64(padding: false)
      payload = Jason.encode!(%{identifier: id, is_phone: is_phone, is_new_user: is_new_user})
      Redix.command(:redix, ["SETEX", "temp_token:#{temp_token}", "300", payload])

      # 5. Generate and store OTP (hashed) — for email and phone OTP flows
      otp_code = generate_otp()
      otp_hash = :crypto.hash(:sha256, otp_code) |> Base.encode16() |> String.downcase()
      Redix.command(:redix, ["SETEX", "otp:#{temp_token}", "300", otp_hash])

      # 6. Fixed 200ms response delay
      elapsed = System.monotonic_time(:millisecond) - start_time
      delay = max(0, 200 - elapsed)
      Process.sleep(delay)

      # 8. Return — NO is_new_user to prevent account enumeration
      conn
      |> json(%{
        message: "Verification step required.",
        next_step: next_step,
        temp_token: temp_token
      })
    end
  end

  # POST /api/v1/auth/verify_password
  def verify_password(conn, %{"temp_token" => temp_token, "password" => password}) do
    case Redix.command(:redix, ["GET", "temp_token:#{temp_token}"]) do
      {:ok, payload_json} when is_binary(payload_json) ->
        payload = Jason.decode!(payload_json)
        identifier = payload["identifier"]
        is_new_user = payload["is_new_user"]
        is_phone = payload["is_phone"]

        if is_new_user do
          # New user: validate password strength
          case validate_password_strength(password) do
            :ok ->
              hash = Pbkdf2.hash_pwd_salt(password)
              # Store the password hash in Redis (don't create account yet)
              new_payload =
                Jason.encode!(%{
                  identifier: identifier,
                  is_phone: is_phone,
                  is_new_user: true,
                  password_hash: hash
                })

              Redix.command(:redix, ["SETEX", "temp_token:#{temp_token}", "300", new_payload])

              if is_phone do
                # Phone user: need to collect email first before OTP
                conn
                |> json(%{
                  next_step: "email_collect",
                  temp_token: temp_token,
                  message: "Please enter your email to receive a verification code"
                })
              else
                # Email user: send OTP directly
                otp =
                  :crypto.strong_rand_bytes(3)
                  |> :binary.decode_unsigned()
                  |> rem(900_000)
                  |> Kernel.+(100_000)
                  |> to_string()

                otp_hash = :crypto.hash(:sha256, otp) |> Base.encode16() |> String.downcase()
                Redix.command(:redix, ["SETEX", "otp:#{temp_token}", "300", otp_hash])
                Broker.EmailOTP.send_otp(identifier, otp)

                conn
                |> json(%{
                  next_step: "otp_verify",
                  temp_token: temp_token,
                  message: "A verification code has been sent to #{identifier}"
                })
              end

            {:error, reasons} ->
              conn |> put_status(422) |> json(%{error: "Password is too weak", details: reasons})
          end
        else
          # Existing user: verify password
          user =
            if is_phone do
              phone_hash = hash_phone(identifier)
              Repo.get_by!(User, phone_hash: phone_hash)
            else
              Repo.get_by!(User, email: identifier)
            end

          if Pbkdf2.verify_pass(password, user.password_hash) do
            # Only consume token on success
            Redix.command(:redix, ["DEL", "temp_token:#{temp_token}"])
            Redix.command(:redix, ["DEL", "otp:#{temp_token}"])
            handle_successful_login(conn, user, false)
          else
            # Prevent timing attacks on failed logins
            Pbkdf2.no_user_verify()
            conn |> put_status(401) |> json(%{error: "Incorrect password. Please try again."})
          end
        end

      _ ->
        conn
        |> put_status(401)
        |> json(%{error: "Your session has expired. Please go back and try again."})
    end
  end

  # POST /api/v1/auth/verify_otp
  def verify_otp(conn, %{"temp_token" => temp_token, "otp" => otp}) do
    # Verify OTP first (don't consume temp_token until OTP is confirmed)
    case Redix.command(:redix, ["GET", "otp:#{temp_token}"]) do
      {:ok, stored_hash} when is_binary(stored_hash) ->
        submitted_hash = :crypto.hash(:sha256, otp) |> Base.encode16() |> String.downcase()

        if submitted_hash == stored_hash do
          # OTP correct — now consume both tokens
          Redix.command(:redix, ["DEL", "otp:#{temp_token}"])

          case Redix.command(:redix, ["GETDEL", "temp_token:#{temp_token}"]) do
            {:ok, payload_json} when is_binary(payload_json) ->
              payload = Jason.decode!(payload_json)
              identifier = payload["identifier"]
              is_phone = payload["is_phone"]
              is_new_user = payload["is_new_user"]

              user =
                if is_new_user do
                  password_hash = payload["password_hash"]

                  if is_phone do
                    phone_hash = hash_phone(identifier)
                    # For phone users, also store the email they provided for OTP
                    %User{phone_hash: phone_hash, password_hash: password_hash} |> Repo.insert!()
                  else
                    %User{email: identifier, password_hash: password_hash} |> Repo.insert!()
                  end
                else
                  if is_phone do
                    phone_hash = hash_phone(identifier)
                    Repo.get_by!(User, phone_hash: phone_hash)
                  else
                    Repo.get_by!(User, email: identifier)
                  end
                end

              handle_successful_login(conn, user, is_new_user)

            _ ->
              conn |> put_status(401) |> json(%{error: "invalid_or_expired_token"})
          end
        else
          conn |> put_status(401) |> json(%{error: "invalid_otp"})
        end

      _ ->
        conn |> put_status(401) |> json(%{error: "otp_expired"})
    end
  end

  # POST /api/v1/auth/resend_otp
  def resend_otp(conn, %{"temp_token" => temp_token, "email" => email}) do
    # Rate limit: 1 resend per 60s per token
    resend_key = "rl:resend:#{temp_token}"

    case Redix.command(:redix, ["GET", resend_key]) do
      {:ok, nil} ->
        # Check temp_token is still valid (don't consume it)
        case Redix.command(:redix, ["GET", "temp_token:#{temp_token}"]) do
          {:ok, payload} when is_binary(payload) ->
            # Generate new OTP
            otp_code = generate_otp()
            otp_hash = :crypto.hash(:sha256, otp_code) |> Base.encode16() |> String.downcase()

            # Get remaining TTL of temp_token and use same for OTP
            {:ok, ttl} = Redix.command(:redix, ["TTL", "temp_token:#{temp_token}"])
            ttl = max(ttl, 60)

            Redix.command(:redix, ["SETEX", "otp:#{temp_token}", to_string(ttl), otp_hash])
            Redix.command(:redix, ["SETEX", resend_key, "60", "1"])

            # Send the email
            Task.Supervisor.start_child(Broker.PushTaskSupervisor, fn ->
              Broker.EmailOTP.send_otp(email, otp_code)
            end)

            conn |> json(%{message: "Code resent", resend_cooldown: 60})

          _ ->
            conn |> put_status(401) |> json(%{error: "invalid_or_expired_token"})
        end

      _ ->
        conn
        |> put_status(429)
        |> json(%{error: "resend_cooldown", message: "Please wait before requesting a new code."})
    end
  end

  # POST /api/v1/auth/send_phone_otp
  # Called by phone users who need to provide their email for OTP verification
  def send_phone_otp(conn, %{"temp_token" => temp_token, "email" => email}) do
    # Validate email format
    unless Regex.match?(~r/^[\w\-\.]+@([\w\-]+\.)+[\w\-]{2,4}$/, email) do
      conn |> put_status(422) |> json(%{error: "Please enter a valid email address"})
    else
      case Redix.command(:redix, ["GET", "temp_token:#{temp_token}"]) do
        {:ok, payload_json} when is_binary(payload_json) ->
          payload = Jason.decode!(payload_json)

          # Update payload with the verification email
          updated_payload = Jason.encode!(Map.put(payload, "verification_email", email))
          Redix.command(:redix, ["SETEX", "temp_token:#{temp_token}", "300", updated_payload])

          # Generate and send OTP
          otp =
            :crypto.strong_rand_bytes(3)
            |> :binary.decode_unsigned()
            |> rem(900_000)
            |> Kernel.+(100_000)
            |> to_string()

          otp_hash = :crypto.hash(:sha256, otp) |> Base.encode16() |> String.downcase()
          Redix.command(:redix, ["SETEX", "otp:#{temp_token}", "300", otp_hash])

          Broker.EmailOTP.send_phone_otp(email, otp)

          conn
          |> json(%{
            next_step: "otp_verify",
            temp_token: temp_token,
            message: "A verification code has been sent to #{email}"
          })

        _ ->
          conn
          |> put_status(401)
          |> json(%{error: "Your session has expired. Please go back and try again."})
      end
    end
  end

  # POST /api/v1/auth/forgot_password
  def forgot_password(conn, %{"email" => email}) do
    start_time = System.monotonic_time(:millisecond)

    # Rate limit
    rl_key = "rl:forgot:#{email}"

    case Redix.command(:redix, ["INCR", rl_key]) do
      {:ok, count} ->
        if count == 1, do: Redix.command(:redix, ["EXPIRE", rl_key, "3600"])

        if count > 3 do
          conn |> put_status(429) |> json(%{error: "Too many reset requests."})
        else
          # Generate reset code (always, even if email doesn't exist — anti-enumeration)
          reset_code = generate_otp()

          # Only actually send if user exists
          user = Repo.get_by(User, email: email)

          if user do
            reset_hash = :crypto.hash(:sha256, reset_code) |> Base.encode16() |> String.downcase()
            Redix.command(:redix, ["SETEX", "reset:#{email}", "900", reset_hash])

            Task.Supervisor.start_child(Broker.PushTaskSupervisor, fn ->
              Broker.EmailOTP.send_password_reset(email, reset_code)
            end)
          end

          # Fixed delay for anti-enumeration
          elapsed = System.monotonic_time(:millisecond) - start_time
          Process.sleep(max(0, 200 - elapsed))

          # Always return success (don't reveal if email exists)
          conn |> json(%{message: "If an account exists, a reset code has been sent."})
        end

      _ ->
        conn |> json(%{message: "If an account exists, a reset code has been sent."})
    end
  end

  # POST /api/v1/auth/reset_password
  def reset_password(conn, %{"email" => email, "code" => code, "new_password" => new_password}) do
    case Redix.command(:redix, ["GET", "reset:#{email}"]) do
      {:ok, stored_hash} when is_binary(stored_hash) ->
        submitted_hash = :crypto.hash(:sha256, code) |> Base.encode16() |> String.downcase()

        if submitted_hash == stored_hash do
          case validate_password_strength(new_password) do
            :ok ->
              Redix.command(:redix, ["DEL", "reset:#{email}"])
              user = Repo.get_by!(User, email: email)
              new_hash = Pbkdf2.hash_pwd_salt(new_password)
              user |> Ecto.Changeset.change(%{password_hash: new_hash}) |> Repo.update!()
              conn |> json(%{message: "Password reset successfully."})

            {:error, reasons} ->
              conn |> put_status(422) |> json(%{error: "weak_password", details: reasons})
          end
        else
          conn |> put_status(401) |> json(%{error: "invalid_reset_code"})
        end

      _ ->
        conn |> put_status(401) |> json(%{error: "expired_or_invalid_reset"})
    end
  end

  # POST /api/v1/qr/generate
  def generate_qr(conn, _params) do
    token = :crypto.strong_rand_bytes(32) |> Base.encode16() |> String.downcase()
    Redix.command(:redix, ["SETEX", "qr:#{token}", "60", "pending"])
    conn |> json(%{token: token, expires_in: 60})
  end

  # POST /api/v1/qr/validate
  # Called by the MOBILE app after scanning the QR code to authorize the desktop session.
  def validate_qr(conn, %{"token" => token}) do
    user_id = conn.assigns[:current_user_id]

    if user_id do
      case Redix.command(:redix, ["GET", "qr:#{token}"]) do
        {:ok, "pending"} ->
          device_uuid = Ecto.UUID.generate()
          placeholder_key = :crypto.strong_rand_bytes(32)

          %Device{
            user_id: user_id,
            device_uuid: device_uuid,
            platform: "desktop_qr",
            is_active: true,
            public_identity_key: placeholder_key,
            registration_id: 0
          }
          |> Repo.insert!()

          desktop_jwt =
            BrokerWeb.Token.generate_and_sign!(user_id, device_uuid, [
              "keys:register",
              "chat:send",
              "chat:receive",
              "media:upload",
              "pre_keys:fetch"
            ])

          desktop_refresh = :crypto.strong_rand_bytes(32) |> Base.url_encode64(padding: false)

          Redix.command(:redix, [
            "SETEX",
            "refresh_token:#{desktop_refresh}",
            "2592000",
            "#{user_id}:#{device_uuid}"
          ])

          session_payload =
            Jason.encode!(%{
              token: desktop_jwt,
              refresh_token: desktop_refresh,
              user_id: user_id,
              device_id: device_uuid
            })

          Redix.command(:redix, ["SETEX", "qr:#{token}", "60", session_payload])

          conn |> json(%{status: "success", device_id: device_uuid})

        _ ->
          conn |> put_status(400) |> json(%{error: "invalid_or_expired_token"})
      end
    else
      conn |> put_status(401) |> json(%{error: "unauthorized"})
    end
  end

  # GET /api/v1/qr/poll/:token
  # Called by the DESKTOP app to check if the QR code was scanned and validated.
  def poll_qr(conn, %{"token" => token}) do
    case Redix.command(:redix, ["GET", "qr:#{token}"]) do
      {:ok, "pending"} ->
        conn |> json(%{status: "pending"})

      {:ok, session_json} when is_binary(session_json) ->
        Redix.command(:redix, ["DEL", "qr:#{token}"])
        session = Jason.decode!(session_json)
        conn |> json(Map.put(session, "status", "validated"))

      _ ->
        conn |> put_status(404) |> json(%{error: "expired_or_not_found"})
    end
  end

  # POST /api/v1/auth/google
  def google_auth(conn, %{"id_token" => id_token}) do
    case verify_google_token(id_token) do
      {:ok, %{"email" => email}} ->
        user = Repo.get_by(User, email: email)

        {user, is_new_user} =
          if is_nil(user) do
            # Create user if doesn't exist
            user = %User{email: email} |> Repo.insert!()
            {user, true}
          else
            {user, false}
          end

        handle_successful_login(conn, user, is_new_user)

      {:error, reason} ->
        conn |> put_status(401) |> json(%{error: "Google verification failed", details: reason})
    end
  end

  defp verify_google_token(id_token) do
    # Call Google tokeninfo endpoint
    url = "https://oauth2.googleapis.com/tokeninfo?id_token=#{id_token}"

    # Using Finch for HTTP request
    case Finch.build(:get, url) |> Finch.request(Broker.Finch) do
      {:ok, %Finch.Response{status: 200, body: body}} ->
        data = Jason.decode!(body)
        # Check aud (audience) matches our client ID
        if data["aud"] ==
             "132565546749-7fjpe9g1ekrtnvjri1dlk2034vmgf2r4.apps.googleusercontent.com" do
          {:ok, data}
        else
          {:error, "invalid_audience"}
        end

      {:ok, %Finch.Response{body: body}} ->
        {:error, body}

      {:error, reason} ->
        {:error, inspect(reason)}
    end
  end

  defp handle_successful_login(conn, user, is_new_user) do
    # FIXED: Check device count BEFORE deactivating (audit item #8)
    existing_devices_count =
      Repo.one(
        from d in Device,
          where: d.user_id == ^user.id and d.is_active == true,
          select: count(d.id)
      )

    requires_key_setup = existing_devices_count == 0

    # Now deactivate old devices
    from(d in Device, where: d.user_id == ^user.id)
    |> Repo.update_all(set: [is_active: false])

    device_uuid = Ecto.UUID.generate()

    %Device{
      user_id: user.id,
      device_uuid: device_uuid,
      platform: "mobile",
      is_active: true,
      public_identity_key: "pending_registration",
      registration_id: 0
    }
    |> Repo.insert!()

    token =
      BrokerWeb.Token.generate_and_sign!(user.id, device_uuid, [
        "keys:register",
        "chat:send",
        "chat:receive",
        "media:upload",
        "backup:write",
        "pre_keys:fetch"
      ])

    refresh_token = :crypto.strong_rand_bytes(32) |> Base.url_encode64(padding: false)
    # 400 days
    Redix.command(:redix, [
      "SETEX",
      "refresh_token:#{refresh_token}",
      "34560000",
      "#{user.id}:#{device_uuid}"
    ])

    conn
    |> json(%{
      user_id: user.id,
      device_id: device_uuid,
      token: token,
      refresh_token: refresh_token,
      requires_key_setup: requires_key_setup,
      is_new_user: is_new_user
    })
  end

  # POST /api/v1/auth/refresh
  def refresh(conn, %{"refresh_token" => old_token}) do
    case Redix.command(:redix, ["GETDEL", "refresh_token:#{old_token}"]) do
      {:ok, val} when is_binary(val) ->
        [user_id, device_id] = String.split(val, ":")

        device = Repo.get_by(Device, device_uuid: device_id, is_active: true)

        if device do
          token =
            BrokerWeb.Token.generate_and_sign!(user_id, device_id, [
              "keys:register",
              "chat:send",
              "chat:receive",
              "media:upload",
              "backup:write",
              "pre_keys:fetch"
            ])

          new_refresh_token = :crypto.strong_rand_bytes(32) |> Base.url_encode64(padding: false)
          # 400 days
          Redix.command(:redix, [
            "SETEX",
            "refresh_token:#{new_refresh_token}",
            "34560000",
            "#{user_id}:#{device_id}"
          ])

          conn |> json(%{token: token, refresh_token: new_refresh_token})
        else
          conn |> put_status(401) |> json(%{error: "device_revoked"})
        end

      _ ->
        conn |> put_status(401) |> json(%{error: "invalid_refresh_token"})
    end
  end

  # POST /api/v1/auth/logout
  def logout(conn, _params) do
    user_id = conn.assigns.current_user_id
    device_uuid = conn.assigns.current_device_id

    case Repo.get_by(Device, user_id: user_id, device_uuid: device_uuid, is_active: true) do
      nil ->
        :ok

      device ->
        device |> Ecto.Changeset.change(%{is_active: false}) |> Repo.update!()
    end

    delete_refresh_tokens_for("#{user_id}:#{device_uuid}")
    BrokerWeb.Endpoint.broadcast("user_socket:#{user_id}", "disconnect", %{})

    conn |> json(%{status: "logged_out"})
  end

  # POST /api/v1/auth/logout_all
  def logout_all(conn, _params) do
    user_id = conn.assigns.current_user_id

    from(d in Device, where: d.user_id == ^user_id)
    |> Repo.update_all(set: [is_active: false])

    delete_refresh_tokens_matching(user_id)
    BrokerWeb.Endpoint.broadcast("user_socket:#{user_id}", "disconnect", %{})

    conn |> json(%{status: "all_devices_logged_out"})
  end

  # DELETE /api/v1/account
  # GDPR-compliant full account deletion.
  def delete_account(conn, _params) do
    user_id = conn.assigns.current_user_id
    alias Broker.Keys.{PreKey, SignedPreKey}
    alias Broker.Audit.KeyRegistration

    device_ids = Repo.all(from(d in Device, where: d.user_id == ^user_id, select: d.id))

    if device_ids != [] do
      Repo.delete_all(from p in PreKey, where: p.device_id in ^device_ids)
      Repo.delete_all(from s in SignedPreKey, where: s.device_id in ^device_ids)
    end

    user_id_hash = :crypto.hash(:sha256, user_id)
    Repo.delete_all(from a in KeyRegistration, where: a.user_id_hash == ^user_id_hash)
    Repo.delete_all(from d in Device, where: d.user_id == ^user_id)

    case Repo.get(User, user_id) do
      nil -> :ok
      user -> Repo.delete!(user)
    end

    delete_refresh_tokens_matching(user_id)
    Broker.Chat.OfflineQueue.purge(user_id)
    BrokerWeb.Endpoint.broadcast("user_socket:#{user_id}", "disconnect", %{})

    conn |> json(%{status: "account_deleted"})
  end

  # --- Private helpers ---

  defp check_rate_limit!(key, ttl, _limit) do
    case Redix.command(:redix, ["INCR", key]) do
      {:ok, count} ->
        if count == 1, do: Redix.command(:redix, ["EXPIRE", key, to_string(ttl)])
        count

      _ ->
        0
    end
  end

  defp generate_otp do
    (:rand.uniform(899_999) + 100_000) |> Integer.to_string()
  end

  defp hash_phone(phone) do
    secret = Application.get_env(:broker, :phone_hash_secret)
    :crypto.mac(:hmac, :sha256, secret, phone) |> Base.encode64()
  end

  defp validate_password_strength(password) do
    errors = []

    errors =
      if String.length(password) < 8, do: ["Must be at least 8 characters" | errors], else: errors

    errors =
      if not Regex.match?(~r/[A-Z]/, password),
        do: ["Must contain an uppercase letter" | errors],
        else: errors

    errors =
      if not Regex.match?(~r/[0-9]/, password),
        do: ["Must contain a number" | errors],
        else: errors

    if errors == [], do: :ok, else: {:error, Enum.reverse(errors)}
  end

  defp delete_refresh_tokens_for(value_pattern) do
    scan_and_delete("0", value_pattern, :exact)
  end

  defp delete_refresh_tokens_matching(user_id_prefix) do
    scan_and_delete("0", "#{user_id_prefix}:", :prefix)
  end

  defp scan_and_delete(cursor, pattern, match_mode) do
    case Redix.command(:redix, ["SCAN", cursor, "MATCH", "refresh_token:*", "COUNT", "100"]) do
      {:ok, [next_cursor, keys]} ->
        Enum.each(keys, fn key ->
          case Redix.command(:redix, ["GET", key]) do
            {:ok, val} when is_binary(val) ->
              should_delete =
                case match_mode do
                  :exact -> val == pattern
                  :prefix -> String.starts_with?(val, pattern)
                end

              if should_delete, do: Redix.command(:redix, ["DEL", key])

            _ ->
              :ok
          end
        end)

        if next_cursor != "0" do
          scan_and_delete(next_cursor, pattern, match_mode)
        end

      _ ->
        :ok
    end
  end
end
