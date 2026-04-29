defmodule Broker.Repo.Migrations.CreateGroupsAndMembers do
  use Ecto.Migration

  def change do
    create table(:groups, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :name, :string
      add :avatar_url, :string
      add :created_by_user_id, references(:users, type: :binary_id, on_delete: :nothing)

      timestamps(type: :utc_datetime)
    end

    create index(:groups, [:created_by_user_id])

    create table(:group_members, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :group_id, references(:groups, type: :binary_id, on_delete: :delete_all)
      add :user_id, references(:users, type: :binary_id, on_delete: :nothing)
      add :device_id, references(:devices, type: :binary_id, on_delete: :nothing)
      add :role, :string, default: "member"
      add :is_active, :boolean, default: true, null: false
      add :joined_at, :utc_datetime

      timestamps(type: :utc_datetime)
    end

    create index(:group_members, [:group_id])
    create index(:group_members, [:user_id])
    create index(:group_members, [:device_id])
    create unique_index(:group_members, [:group_id, :user_id, :device_id])
  end
end
