defmodule Broker.Repo.Migrations.CreateDevices do
  use Ecto.Migration

  def change do
    create table(:devices, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :user_id, references(:users, on_delete: :delete_all, type: :binary_id), null: false
      add :device_uuid, :string, null: false
      add :push_token, :string
      add :platform, :string, null: false
      add :public_identity_key, :binary, null: false
      add :registration_id, :integer, null: false
      add :last_seen, :utc_datetime
      add :is_active, :boolean, default: true, null: false

      timestamps(type: :utc_datetime)
    end

    create index(:devices, [:user_id, :is_active])
  end
end
