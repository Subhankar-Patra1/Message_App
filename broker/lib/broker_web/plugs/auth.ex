defmodule BrokerWeb.Plugs.Auth do
  import Plug.Conn
  alias Broker.Repo
  alias Broker.Accounts.Device

  def init(opts), do: opts

  def call(conn, _opts) do
    case get_req_header(conn, "authorization") do
      ["Bearer " <> token] ->
        case BrokerWeb.Token.verify_jwt(token) do
          {:ok, claims} ->
            sub = claims["sub"]
            dev = claims["dev"]

            # Check if device is active
            device = Repo.get_by(Device, device_uuid: dev, is_active: true)

            if device do
              conn
              |> assign(:current_user_id, sub)
              |> assign(:current_device_id, dev)
              |> assign(:token_scopes, claims["scopes"] || [])
            else
              conn
              |> put_status(401)
              |> Phoenix.Controller.json(%{error: "device_revoked"})
              |> halt()
            end

          {:error, _reason} ->
            conn
            |> put_status(401)
            |> Phoenix.Controller.json(%{error: "invalid_token"})
            |> halt()
        end

      _ ->
        conn
        |> put_status(401)
        |> Phoenix.Controller.json(%{error: "missing_token"})
        |> halt()
    end
  end
end
