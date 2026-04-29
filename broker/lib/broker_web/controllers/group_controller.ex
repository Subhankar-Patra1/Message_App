defmodule BrokerWeb.GroupController do
  use BrokerWeb, :controller
  require Logger
  alias Broker.Chat

  action_fallback BrokerWeb.FallbackController

  def create(conn, %{"group" => group_params}) do
    current_user_id = conn.assigns.current_user_id

    with {:ok, group} <- Chat.create_group(current_user_id, group_params) do
      conn
      |> put_status(:created)
      |> render(:show, group: group)
    end
  end

  def show(conn, %{"id" => id}) do
    current_user_id = conn.assigns.current_user_id

    if Chat.is_member?(id, current_user_id) do
      group = Chat.get_group!(id)
      members = Chat.list_active_members(id)
      render(conn, :show, group: group, members: members)
    else
      {:error, :forbidden}
    end
  end

  def add_member(conn, %{"id" => id, "user_id" => user_id}) do
    current_user_id = conn.assigns.current_user_id

    if Chat.is_admin?(id, current_user_id) do
      Chat.add_member(id, user_id)
      # Trigger key rotation event (Phase 4) - we can log it for now
      Logger.info("Member #{user_id} added to group #{id} by #{current_user_id}")

      conn
      |> put_status(:ok)
      |> json(%{message: "Member added"})
    else
      {:error, :forbidden}
    end
  end

  def remove_member(conn, %{"id" => id, "user_id" => user_id}) do
    current_user_id = conn.assigns.current_user_id

    if Chat.is_admin?(id, current_user_id) or current_user_id == user_id do
      Chat.remove_member(id, user_id)
      # Trigger key rotation event (Phase 4)
      Logger.info("Member #{user_id} removed from group #{id}")

      conn
      |> put_status(:ok)
      |> json(%{message: "Member removed"})
    else
      {:error, :forbidden}
    end
  end
end
