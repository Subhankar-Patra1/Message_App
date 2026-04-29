defmodule Broker.Repo.Migrations.CreateFrankingRecords do
  use Ecto.Migration

  def change do
    create table(:franking_records, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :message_id, :string, null: false
      add :reporter_device_id, references(:devices, on_delete: :nilify_all, type: :binary_id)
      add :mac, :binary, null: false
      add :reported_at, :utc_datetime, null: false
      add :status, :string, default: "pending", null: false

      timestamps(type: :utc_datetime)
    end

    create index(:franking_records, [:message_id])
  end
end
