defmodule BrokerWeb.DeviceController do
  use BrokerWeb, :controller
  alias Broker.Repo
  alias Broker.Accounts.Device
  import Ecto.Query

  action_fallback BrokerWeb.FallbackController

  plug BrokerWeb.Plugs.RequireScope, "chat:send"

  @doc """
  PUT /api/v1/devices/push_token

  Updates the FCM push token for the current user's active device.
  Called by the Flutter app on startup and whenever FCM issues a new token.
  """
  def update_push_token(conn, %{"push_token" => push_token}) do
    user_id = conn.assigns.current_user_id
    device_id = conn.assigns.current_device_id

    device =
      Repo.one(
        from d in Device,
          where: d.user_id == ^user_id and d.device_uuid == ^device_id and d.is_active == true,
          limit: 1
      )

    case device do
      nil ->
        conn |> put_status(404) |> json(%{error: "device_not_found"})

      device ->
        device
        |> Ecto.Changeset.change(%{push_token: push_token})
        |> Repo.update!()

        conn |> json(%{status: "ok"})
    end
  end

  def update_push_token(conn, _),
    do: conn |> put_status(400) |> json(%{error: "missing_push_token"})
end
