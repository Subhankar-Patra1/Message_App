defmodule BrokerWeb.UserSocket do
  use Phoenix.Socket

  channel "encrypted_chat:*", BrokerWeb.ChatChannel
  channel "group_chat:*", BrokerWeb.ChatChannel

  def connect(%{"token" => token}, socket, _connect_info) do
    case BrokerWeb.Token.verify_jwt(token) do
      {:ok, claims} ->
        user_id = claims["sub"]
        device_id = claims["dev"]
        {:ok, assign(socket, user_id: user_id, device_id: device_id)}

      {:error, _} ->
        :error
    end
  end

  def connect(_params, _socket, _connect_info) do
    :error
  end

  def id(socket), do: "user_socket:#{socket.assigns.user_id}"
end
