defmodule Broker.Repo.Migrations.CreatePreKeys do
  use Ecto.Migration

  def change do
    create table(:pre_keys, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :device_id, references(:devices, on_delete: :delete_all, type: :binary_id), null: false
      add :key_id, :integer, null: false
      add :public_key, :binary, null: false
      add :is_used, :boolean, default: false, null: false

      timestamps(type: :utc_datetime)
    end

    create index(:pre_keys, [:device_id, :is_used])
    create unique_index(:pre_keys, [:device_id, :key_id])
  end
end
