defmodule BrokerWeb.FallbackController do
  use BrokerWeb, :controller
  require Logger

  def call(conn, {:error, %Ecto.Changeset{} = changeset}) do
    Logger.warning("Changeset validation failed", errors: inspect(changeset.errors))

    conn
    |> put_status(:bad_request)
    |> json(%{error: "invalid_format"})
  end

  def call(conn, {:error, :invalid_format}) do
    conn
    |> put_status(:bad_request)
    |> json(%{error: "invalid_format"})
  end

  def call(conn, {:error, :unsupported_version}) do
    conn
    |> put_status(:bad_request)
    |> json(%{error: "unsupported_version"})
  end

  def call(conn, {:error, :missing_version}) do
    conn
    |> put_status(:bad_request)
    |> json(%{error: "missing_version"})
  end

  def call(conn, {:error, :not_found}) do
    conn
    |> put_status(:not_found)
    |> json(%{error: "user_not_found"})
  end

  def call(conn, {:error, :conflict}) do
    conn
    |> put_status(:conflict)
    |> json(%{error: "key_conflict"})
  end

  def call(conn, {:error, :forbidden}) do
    conn
    |> put_status(:forbidden)
    |> json(%{error: "forbidden"})
  end

  def call(conn, {:error, step, reason, _changes}) do
    Logger.error("Transaction failed at step #{step}", reason: inspect(reason))

    conn
    |> put_status(:bad_request)
    |> json(%{
      error: "transaction_failed",
      step: to_string(step),
      reason: inspect(reason)
    })
  end
end
