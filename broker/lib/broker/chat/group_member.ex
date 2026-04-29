defmodule Broker.Chat.GroupMember do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "group_members" do
    field :role, :string, default: "member"
    field :is_active, :boolean, default: true
    field :joined_at, :utc_datetime

    belongs_to :group, Broker.Chat.Group
    belongs_to :user, Broker.Accounts.User
    belongs_to :device, Broker.Accounts.Device

    timestamps(type: :utc_datetime)
  end

  def changeset(group_member, attrs) do
    group_member
    |> cast(attrs, [:group_id, :user_id, :device_id, :role, :is_active, :joined_at])
    |> validate_required([:group_id, :user_id, :device_id])
  end
end
