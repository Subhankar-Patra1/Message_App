defmodule Broker.Repo.Migrations.CreateSignedPreKeys do
  use Ecto.Migration

  def change do
    create table(:signed_pre_keys, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :device_id, references(:devices, on_delete: :delete_all, type: :binary_id), null: false
      add :key_id, :integer, null: false
      add :public_key, :binary, null: false
      add :signature, :binary, null: false
      add :rotated_at, :utc_datetime
      add :is_active, :boolean, default: true, null: false

      timestamps(type: :utc_datetime)
    end

    create index(:signed_pre_keys, [:device_id, :is_active])
  end
end
