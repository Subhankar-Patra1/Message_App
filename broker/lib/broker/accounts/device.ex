defmodule Broker.Accounts.Device do
  use Ecto.Schema
  import Ecto.Changeset
  alias Broker.CryptoContract

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "devices" do
    field :device_uuid, :string
    field :push_token, :string
    field :platform, :string
    field :public_identity_key, :binary
    field :registration_id, :integer
    field :last_seen, :utc_datetime
    field :is_active, :boolean, default: true

    belongs_to :user, Broker.Accounts.User

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(device, attrs) do
    device
    |> cast(attrs, [
      :user_id,
      :device_uuid,
      :push_token,
      :platform,
      :public_identity_key,
      :registration_id,
      :last_seen,
      :is_active
    ])
    |> validate_required([
      :user_id,
      :device_uuid,
      :platform,
      :public_identity_key,
      :registration_id
    ])
    |> validate_inclusion(:platform, ["ios", "android"])
    |> validate_number(:registration_id, greater_than_or_equal_to: 0, less_than: 4_294_967_296)
    |> CryptoContract.reject_private_key_hints([:public_identity_key])
    |> validate_identity_key_length()
  end

  defp validate_identity_key_length(changeset) do
    validate_change(changeset, :public_identity_key, fn :public_identity_key, value ->
      if byte_size(value) == 32 do
        []
      else
        [public_identity_key: "invalid_format"]
      end
    end)
  end
end
