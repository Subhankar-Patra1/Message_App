defmodule Broker.Repo.Migrations.CreateAuditKeyRegistrations do
  use Ecto.Migration

  def change do
    create table(:audit_key_registrations, primary_key: false) do
      add :id, :binary_id, primary_key: true
      add :user_id_hash, :binary, null: false
      add :device_id_hash, :binary, null: false
      add :action, :string, null: false
      add :pre_keys_count, :integer, null: false
      add :ip_hash, :binary
      add :user_agent_hash, :binary

      timestamps(type: :utc_datetime, updated_at: false)
    end

    create index(:audit_key_registrations, [:user_id_hash])
  end
end
