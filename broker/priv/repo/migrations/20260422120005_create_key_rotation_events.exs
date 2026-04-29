defmodule Broker.Repo.Migrations.CreateKeyRotationEvents do
  use Ecto.Migration

  def change do
    create table(:key_rotation_events, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :device_id, references(:devices, on_delete: :delete_all, type: :binary_id), null: false
      add :key_type, :string, null: false
      add :old_key_id, :integer
      add :new_key_id, :integer, null: false
      add :rotated_at, :utc_datetime, null: false

      timestamps(type: :utc_datetime)
    end

    create index(:key_rotation_events, [:device_id])
  end
end
