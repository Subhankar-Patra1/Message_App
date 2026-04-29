defmodule Broker.Chat do
  import Ecto.Query, warn: false
  alias Broker.Repo
  alias Broker.Chat.{Group, GroupMember}
  alias Broker.Accounts.Device

  def create_group(creator_id, attrs \\ %{}) do
    Repo.transaction(fn ->
      %Group{}
      |> Group.changeset(Map.put(attrs, "created_by_user_id", creator_id))
      |> Repo.insert!()
      |> add_creator_to_group(creator_id)
    end)
  end

  defp add_creator_to_group(group, creator_id) do
    # Get all active devices for the creator
    devices = Repo.all(from d in Device, where: d.user_id == ^creator_id and d.is_active == true)

    Enum.each(devices, fn device ->
      %GroupMember{}
      |> GroupMember.changeset(%{
        group_id: group.id,
        user_id: creator_id,
        device_id: device.id,
        role: "admin",
        joined_at: DateTime.utc_now()
      })
      |> Repo.insert!()
    end)

    group
  end

  def get_group!(id), do: Repo.get!(Group, id)

  def list_active_members(group_id) do
    Repo.all(
      from gm in GroupMember,
        where: gm.group_id == ^group_id and gm.is_active == true,
        preload: [:user, :device]
    )
  end

  def add_member(group_id, user_id, role \\ "member") do
    # Get all active devices for the user
    devices = Repo.all(from d in Device, where: d.user_id == ^user_id and d.is_active == true)

    Enum.map(devices, fn device ->
      %GroupMember{}
      |> GroupMember.changeset(%{
        group_id: group_id,
        user_id: user_id,
        device_id: device.id,
        role: role,
        joined_at: DateTime.utc_now()
      })
      |> Repo.insert()
    end)
  end

  def remove_member(group_id, user_id) do
    from(gm in GroupMember, where: gm.group_id == ^group_id and gm.user_id == ^user_id)
    |> Repo.update_all(set: [is_active: false])
  end

  def is_member?(group_id, user_id) do
    Repo.exists?(
      from gm in GroupMember,
        where: gm.group_id == ^group_id and gm.user_id == ^user_id and gm.is_active == true
    )
  end

  def is_admin?(group_id, user_id) do
    Repo.exists?(
      from gm in GroupMember,
        where:
          gm.group_id == ^group_id and gm.user_id == ^user_id and gm.is_active == true and
            gm.role == "admin"
    )
  end
end
