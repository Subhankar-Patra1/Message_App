defmodule BrokerWeb.HealthController do
  use BrokerWeb, :controller

  @doc """
  GET /healthz

  Returns the health status of the application including
  database and Valkey connectivity. Used by load balancers
  and monitoring systems.
  """
  def check(conn, _params) do
    db_status = check_db()
    redis_status = check_redis()

    overall = if db_status == "ok" and redis_status == "ok", do: "ok", else: "degraded"
    status_code = if overall == "ok", do: 200, else: 503

    conn
    |> put_status(status_code)
    |> json(%{
      status: overall,
      db: db_status,
      redis: redis_status,
      timestamp: DateTime.utc_now() |> DateTime.to_iso8601()
    })
  end

  defp check_db do
    try do
      Ecto.Adapters.SQL.query!(Broker.Repo, "SELECT 1", [])
      "ok"
    rescue
      _ -> "error"
    end
  end

  defp check_redis do
    case Redix.command(:redix, ["PING"]) do
      {:ok, "PONG"} -> "ok"
      _ -> "error"
    end
  end
end
