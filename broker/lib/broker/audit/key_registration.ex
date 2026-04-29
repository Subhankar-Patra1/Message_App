defmodule Broker.Audit.KeyRegistration do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "audit_key_registrations" do
    field :user_id_hash, :binary
    field :device_id_hash, :binary
    field :action, :string
    field :pre_keys_count, :integer
    field :ip_hash, :binary
    field :user_agent_hash, :binary

    timestamps(type: :utc_datetime, updated_at: false)
  end

  def changeset(audit, attrs) do
    audit
    |> cast(attrs, [
      :user_id_hash,
      :device_id_hash,
      :action,
      :pre_keys_count,
      :ip_hash,
      :user_agent_hash
    ])
    |> validate_required([:user_id_hash, :device_id_hash, :action, :pre_keys_count])
  end
end
