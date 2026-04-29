defmodule BrokerWeb.ChatChannel do
  use BrokerWeb, :channel
  require Logger
  alias BrokerWeb.Presence
  alias Broker.Chat.OfflineQueue
  alias Broker.Keys.PreKey
  alias Broker.Accounts.Device
  import Ecto.Query

  # ~64KB
  @max_payload_size 64_000

  def join("encrypted_chat:" <> recipient_id, _payload, socket) do
    normalized_id = normalize_id(recipient_id)

    if socket.assigns.user_id == normalized_id do
      send(self(), :after_join)
      {:ok, socket}
    else
      {:error, %{reason: "unauthorized"}}
    end
  end

  def join("group_chat:" <> group_id, _payload, socket) do
    user_id = socket.assigns.user_id
    normalized_group_id = normalize_id(group_id)

    if Broker.Chat.is_member?(normalized_group_id, user_id) do
      {:ok, assign(socket, :group_id, normalized_group_id)}
    else
      {:error, %{reason: "unauthorized"}}
    end
  end

  defp normalize_id(id) do
    case Ecto.UUID.cast(id) do
      {:ok, uuid} -> uuid
      :error -> :crypto.hash(:md5, id) |> Ecto.UUID.cast!()
    end
  end

  def handle_info(:after_join, socket) do
    user_id = socket.assigns.user_id
    device_id = socket.assigns.device_id

    {:ok, _} =
      Presence.track(socket, user_id, %{
        device_id: device_id,
        joined_at: System.system_time(:second)
      })

    # Start batched drain
    drain_batch(socket, 0)

    # Pre-key replenishment check
    check_pre_key_count(socket, device_id)

    {:noreply, socket}
  end

  def handle_info({:drain_next, offset}, socket) do
    drain_batch(socket, offset)
    {:noreply, socket}
  end

  defp drain_batch(socket, offset) do
    user_id = socket.assigns.user_id
    case OfflineQueue.drain_page(user_id, offset, 50) do
      {:ok, messages, has_more} ->
        valid_messages =
          Enum.reject(messages, fn msg ->
            OfflineQueue.is_recently_delivered?(user_id, msg["msg_id"])
          end)

        Enum.each(valid_messages, fn msg ->
          push(socket, "receive_message", msg)
          OfflineQueue.mark_delivered(user_id, msg["msg_id"])
        end)

        if has_more do
          Process.send_after(self(), {:drain_next, offset + 50}, 100)
        else
          push(socket, "sync_complete", %{total: offset + length(messages)})
        end

      _ ->
        nil
    end
  end

  def handle_in("send_message", payload, socket) do
    with :ok <- validate_schema(payload),
         :ok <- validate_size(payload) do
      raw_recipient_id = payload["recipient_user_id"]
      recipient_id = normalize_id(raw_recipient_id)
      user_id = socket.assigns.user_id
      
      server_ts = System.system_time(:millisecond)
      seq = next_seq(user_id, recipient_id)
      
      payload_with_ts = 
        payload
        |> Map.put("server_ts", server_ts)
        |> Map.put("seq", seq)

      case OfflineQueue.enqueue(recipient_id, payload_with_ts) do
        :ok ->
          if is_online?(raw_recipient_id) do
            case OfflineQueue.drain(recipient_id) do
              {:ok, messages} ->
                Enum.each(messages, fn msg ->
                  BrokerWeb.Endpoint.broadcast(
                    "encrypted_chat:#{raw_recipient_id}",
                    "receive_message",
                    msg
                  )

                  OfflineQueue.mark_delivered(recipient_id, msg["msg_id"])
                end)

              _ ->
                nil
            end
          else
            # Recipient is offline — send a push notification to wake their phone
            Broker.PushNotification.send_async(
              recipient_id,
              "New Message",
              "You have a new encrypted message"
            )
          end

          {:reply, {:ok, %{status: "delivered", server_ts: server_ts, seq: seq}},
           socket}

        error ->
          Logger.error("Failed to write to offline queue: #{inspect(error)}")
          {:reply, {:error, %{reason: "internal_error"}}, socket}
      end
    else
      {:error, reason} ->
        {:reply, {:error, %{reason: reason}}, socket}
    end
  end

  def handle_in("send_group_message", payload, socket) do
    group_id = socket.assigns[:group_id]
    user_id = socket.assigns.user_id

    if is_nil(group_id) do
      {:reply, {:error, %{reason: "not_in_group_topic"}}, socket}
    else
      with :ok <- validate_group_message_schema(payload),
           :ok <- validate_size(payload) do
        members = Broker.Chat.list_active_members(group_id)
        server_ts = System.system_time(:millisecond)
        # Enqueue for each member for offline support
        Enum.each(members, fn member ->
          if member.user_id != user_id do
            group_payload =
              Map.merge(payload, %{
                "group_id" => group_id,
                "sender_id" => user_id,
                "type" => "group_message",
                "server_ts" => server_ts
              })

            Broker.Chat.OfflineQueue.enqueue(member.user_id, group_payload)

            # Push notification for offline group members
            unless is_online?(member.user_id) do
              Broker.PushNotification.send_async(
                member.user_id,
                "Group Message",
                "New message in group chat"
              )
            end
          end
        end)

        # Broadcast to online members in the group channel
        broadcast(
          socket,
          "receive_group_message",
          Map.merge(payload, %{
            "sender_id" => user_id,
            "group_id" => group_id,
            "server_ts" => server_ts
          })
        )

        {:reply, {:ok, %{status: "sent", server_ts: server_ts}}, socket}
      else
        {:error, reason} ->
          {:reply, {:error, %{reason: reason}}, socket}
      end
    end
  end

  # --- Delivery & Read Receipts & Typing ---

  def handle_in("typing", %{"recipient_id" => recipient_id}, socket) do
    user_id = socket.assigns.user_id
    BrokerWeb.Endpoint.broadcast("encrypted_chat:#{recipient_id}", "typing_start", %{"user_id" => user_id})
    {:reply, :ok, socket}
  end

  def handle_in("stop_typing", %{"recipient_id" => recipient_id}, socket) do
    user_id = socket.assigns.user_id
    BrokerWeb.Endpoint.broadcast("encrypted_chat:#{recipient_id}", "typing_stop", %{"user_id" => user_id})
    {:reply, :ok, socket}
  end

  def handle_in("sync_ack", _payload, socket) do
    user_id = socket.assigns.user_id
    OfflineQueue.clear(user_id)
    {:reply, :ok, socket}
  end

  def handle_in("read_cursor", %{"sender_id" => sender_id, "last_read_seq" => seq}, socket) do
    user_id = socket.assigns.user_id
    BrokerWeb.Endpoint.broadcast("encrypted_chat:#{sender_id}", "read_cursor", %{
      "reader_id" => user_id,
      "last_read_seq" => seq
    })
    {:reply, :ok, socket}
  end

  def handle_in(
        "delivery_receipt",
        %{"msg_id" => msg_id, "sender_user_id" => sender_user_id},
        socket
      ) do
    receipt = %{
      "msg_id" => msg_id,
      "status" => "delivered",
      "timestamp" => System.system_time(:millisecond),
      "by" => socket.assigns.user_id
    }

    # Forward the receipt to the original sender's channel
    BrokerWeb.Endpoint.broadcast("encrypted_chat:#{sender_user_id}", "receipt", receipt)
    {:reply, :ok, socket}
  end

  def handle_in("read_receipt", %{"msg_id" => msg_id, "sender_user_id" => sender_user_id}, socket) do
    receipt = %{
      "msg_id" => msg_id,
      "status" => "read",
      "timestamp" => System.system_time(:millisecond),
      "by" => socket.assigns.user_id
    }

    BrokerWeb.Endpoint.broadcast("encrypted_chat:#{sender_user_id}", "receipt", receipt)
    {:reply, :ok, socket}
  end

  # --- Private Helpers ---

  defp validate_schema(%{"msg_id" => _, "ciphertext" => _, "recipient_user_id" => _}), do: :ok
  defp validate_schema(_), do: {:error, "invalid_schema"}

  defp validate_group_message_schema(%{
         "msg_id" => _,
         "ciphertext" => _,
         "sender_device_id" => _
       }), do: :ok

  defp validate_group_message_schema(_), do: {:error, "invalid_schema"}

  defp validate_size(payload) do
    if byte_size(Jason.encode!(payload)) <= @max_payload_size do
      :ok
    else
      {:error, "payload_too_large"}
    end
  end

  @pre_key_low_threshold 10

  defp check_pre_key_count(socket, device_id) do
    device = Broker.Repo.get_by(Device, device_uuid: device_id, is_active: true)

    if device do
      remaining =
        Broker.Repo.aggregate(
          from(p in PreKey, where: p.device_id == ^device.id and p.is_used == false),
          :count,
          :id
        )

      if remaining < @pre_key_low_threshold do
        push(socket, "pre_key_low", %{remaining: remaining, threshold: @pre_key_low_threshold})
      end
    end
  end

  defp is_online?(user_id) do
    normalized_id = normalize_id(user_id)

    Presence.list("encrypted_chat:#{user_id}")
    |> Map.has_key?(normalized_id)
  end

  defp next_seq(user_a, user_b) do
    conversation_key = Enum.sort([user_a, user_b]) |> Enum.join(":")
    {:ok, seq} = Redix.command(:redix, ["INCR", "conv_seq:#{conversation_key}"])
    seq
  end

  def terminate(_reason, socket) do
    user_id = socket.assigns.user_id
    if user_id do
      # Update last seen at
      now = DateTime.utc_now() |> DateTime.truncate(:second)
      from(u in Broker.Accounts.User, where: u.id == ^user_id)
      |> Broker.Repo.update_all(set: [last_seen_at: now])
    end
    :ok
  end
end
