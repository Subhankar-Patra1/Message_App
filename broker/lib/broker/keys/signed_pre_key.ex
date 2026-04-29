defmodule Broker.Keys.SignedPreKey do
  use Ecto.Schema
  import Ecto.Changeset
  alias Broker.CryptoContract

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "signed_pre_keys" do
    field :key_id, :integer
    field :public_key, :binary
    field :signature, :binary
    field :rotated_at, :utc_datetime
    field :is_active, :boolean, default: true

    belongs_to :device, Broker.Accounts.Device

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(signed_pre_key, attrs) do
    signed_pre_key
    |> cast(attrs, [:device_id, :key_id, :public_key, :signature, :rotated_at, :is_active])
    |> validate_required([:device_id, :key_id, :public_key, :signature])
    |> validate_number(:key_id, greater_than_or_equal_to: 0, less_than: 4_294_967_296)
    |> CryptoContract.reject_private_key_hints([:public_key])
    |> validate_binary_size(:public_key, 32)
    |> validate_binary_size(:signature, 64)
  end

  defp validate_binary_size(changeset, field, expected) do
    validate_change(changeset, field, fn ^field, value ->
      if is_binary(value) and byte_size(value) == expected do
        []
      else
        [{field, "invalid_format"}]
      end
    end)
  end
end
