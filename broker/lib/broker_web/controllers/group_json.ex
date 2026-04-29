defmodule BrokerWeb.GroupJSON do
  def show(%{group: group, members: members}) do
    %{
      id: group.id,
      name: group.name,
      avatar_url: group.avatar_url,
      created_by_user_id: group.created_by_user_id,
      members: Enum.map(members, &member_json/1)
    }
  end

  def show(%{group: group}) do
    %{
      id: group.id,
      name: group.name,
      avatar_url: group.avatar_url,
      created_by_user_id: group.created_by_user_id
    }
  end

  defp member_json(member) do
    %{
      user_id: member.user_id,
      device_id: member.device_id,
      role: member.role,
      is_active: member.is_active,
      joined_at: member.joined_at
    }
  end
end
