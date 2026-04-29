defmodule BrokerWeb.Plugs.RequireScope do
  import Plug.Conn
  import Phoenix.Controller, only: [json: 2]

  def init(scope), do: scope

  def call(conn, required_scope) do
    scopes = conn.assigns[:token_scopes] || []

    if required_scope in scopes do
      conn
    else
      conn
      |> put_status(:unauthorized)
      |> json(%{error: "unauthorized"})
      |> halt()
    end
  end
end
