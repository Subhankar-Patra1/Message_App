defmodule Broker.Chat.OfflineQueue do
  @moduledoc """
  Manages the offline message queue in Valkey (Redis-compatible).
  Implements the Queue-First Write-Ahead logic.
  """

  @max_queue_size 10_000
  # 30 days
  @ttl_seconds 30 * 24 * 60 * 60

  def enqueue(user_id, payload) do
    queue_key = "offline_msgs:#{user_id}"
    json_payload = Jason.encode!(payload)

    commands = [
      ["MULTI"],
      ["RPUSH", queue_key, json_payload],
      ["LTRIM", queue_key, -@max_queue_size, -1],
      ["EXPIRE", queue_key, @ttl_seconds],
      ["EXEC"]
    ]

    case Redix.pipeline(:redix, commands) do
      {:ok, _results} -> :ok
      error -> error
    end
  end

  def drain(user_id) do
    queue_key = "offline_msgs:#{user_id}"

    commands = [
      ["MULTI"],
      ["LRANGE", queue_key, 0, -1],
      ["DEL", queue_key],
      ["EXEC"]
    ]

    case Redix.pipeline(:redix, commands) do
      {:ok, ["OK", "QUEUED", "QUEUED", [messages, _del_count]]} ->
        {:ok, Enum.map(messages, &Jason.decode!/1)}

      error ->
        error
    end
  end

  def drain_page(user_id, offset, limit) do
    queue_key = "offline_msgs:#{user_id}"

    # +1 to limit to check if there are more
    case Redix.command(:redix, ["LRANGE", queue_key, offset, offset + limit - 1]) do
      {:ok, messages} ->
        has_more = length(messages) == limit
        {:ok, Enum.map(messages, &Jason.decode!/1), has_more}

      error ->
        error
    end
  end

  def clear(user_id) do
    queue_key = "offline_msgs:#{user_id}"
    Redix.command(:redix, ["DEL", queue_key])
    :ok
  end

  def is_recently_delivered?(user_id, msg_id) do
    case Redix.command(:redix, ["GET", "delivered_recent:#{user_id}:#{msg_id}"]) do
      {:ok, "1"} -> true
      _ -> false
    end
  end

  def mark_delivered(user_id, msg_id) do
    Redix.command(:redix, ["SETEX", "delivered_recent:#{user_id}:#{msg_id}", 3600, "1"])
    :ok
  end

  @doc "Permanently delete all offline messages for a user (used in account deletion)."
  def purge(user_id) do
    queue_key = "offline_msgs:#{user_id}"
    Redix.command(:redix, ["DEL", queue_key])
    :ok
  end
end
