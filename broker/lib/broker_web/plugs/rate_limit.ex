defmodule BrokerWeb.Plugs.RateLimit do
  import Plug.Conn
  import Phoenix.Controller, only: [json: 2]

  def init(opts), do: opts

  def call(conn, opts) do
    action_name = Keyword.fetch!(opts, :action)
    scale_ms = Keyword.get(opts, :scale_ms, 300_000)
    limit = Keyword.get(opts, :limit, 10)

    user_id = conn.assigns[:current_user_id]
    key = "#{action_name}:#{user_id}"

    case Hammer.check_rate(key, scale_ms, limit) do
      {:allow, _count} ->
        conn

      {:deny, _limit} ->
        conn
        |> put_status(:too_many_requests)
        |> json(%{error: "rate_limited"})
        |> halt()

      {:error, _reason} ->
        # Fail open: if rate limit backend is down, allow the request but log it
        conn
    end
  end
end
