defmodule Broker.Application do
  # See https://hexdocs.pm/elixir/Application.html
  # for more information on OTP Applications
  @moduledoc false

  use Application

  @impl true
  def start(_type, _args) do
    children = [
      BrokerWeb.Telemetry,
      Broker.Repo,
      {DNSCluster, query: Application.get_env(:broker, :dns_cluster_query) || :ignore},
      {Phoenix.PubSub, name: Broker.PubSub},
      BrokerWeb.Presence,
      {Redix, name: :redix, host: "127.0.0.1", port: 6379},
      {Finch, name: Broker.Finch, pools: %{default: [size: 10, count: 1, protocols: [:http2]]}},
      {Task.Supervisor, name: Broker.PushTaskSupervisor},
      # Start to serve requests, typically the last entry
      BrokerWeb.Endpoint
    ]

    # See https://hexdocs.pm/elixir/Supervisor.html
    # for other strategies and supported options
    opts = [strategy: :one_for_one, name: Broker.Supervisor]
    Supervisor.start_link(children, opts)
  end

  # Tell Phoenix to update the endpoint configuration
  # whenever the application is updated.
  @impl true
  def config_change(changed, _new, removed) do
    BrokerWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
