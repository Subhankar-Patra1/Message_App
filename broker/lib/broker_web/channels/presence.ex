defmodule BrokerWeb.Presence do
  use Phoenix.Presence,
    otp_app: :broker,
    pubsub_server: Broker.PubSub
end
