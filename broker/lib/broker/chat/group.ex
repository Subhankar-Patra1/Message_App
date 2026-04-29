defmodule Broker.Chat.Group do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "groups" do
    field :name, :string
    field :avatar_url, :string
    belongs_to :creator, Broker.Accounts.User, foreign_key: :created_by_user_id

    has_many :members, Broker.Chat.GroupMember

    timestamps(type: :utc_datetime)
  end

  def changeset(group, attrs) do
    group
    |> cast(attrs, [:name, :avatar_url, :created_by_user_id])
    |> validate_required([:created_by_user_id])
  end
end
