defmodule Broker.Keys.UploadPayload do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key false
  embedded_schema do
    field :version, :string
    field :device_id, :string

    embeds_one :identity_key, IdentityKey, primary_key: false do
      field :public, :string
      field :signature, :string
    end

    embeds_many :pre_keys, PreKey, primary_key: false do
      field :key_id, :integer
      field :public, :string
    end

    embeds_one :signed_pre_key, SignedPreKey, primary_key: false do
      field :key_id, :integer
      field :public, :string
      field :signature, :string
    end
  end

  def changeset(payload, attrs) do
    payload
    |> cast(attrs, [:version, :device_id])
    |> validate_required([:version, :device_id])
    |> validate_inclusion(:version, ["1.0"], message: "unsupported_version")
    |> cast_embed(:identity_key, with: &identity_key_changeset/2, required: true)
    |> cast_embed(:pre_keys, with: &pre_key_changeset/2, required: true)
    |> cast_embed(:signed_pre_key, with: &signed_pre_key_changeset/2, required: true)
    |> validate_pre_keys_unique()
  end

  defp identity_key_changeset(schema, attrs) do
    schema
    |> cast(attrs, [:public, :signature])
    |> validate_required([:public, :signature])
    |> Broker.CryptoContract.reject_private_key_hints([:public])
    |> Broker.CryptoContract.validate_base64url_key(:public, 32)
    |> Broker.CryptoContract.validate_base64url_key(:signature, 64)
  end

  defp pre_key_changeset(schema, attrs) do
    schema
    |> cast(attrs, [:key_id, :public])
    |> validate_required([:key_id, :public])
    |> validate_number(:key_id, greater_than_or_equal_to: 0, less_than: 4_294_967_296)
    |> Broker.CryptoContract.reject_private_key_hints([:public])
    |> Broker.CryptoContract.validate_base64url_key(:public, 32)
  end

  defp signed_pre_key_changeset(schema, attrs) do
    schema
    |> cast(attrs, [:key_id, :public, :signature])
    |> validate_required([:key_id, :public, :signature])
    |> validate_number(:key_id, greater_than_or_equal_to: 0, less_than: 4_294_967_296)
    |> Broker.CryptoContract.reject_private_key_hints([:public])
    |> Broker.CryptoContract.validate_base64url_key(:public, 32)
    |> Broker.CryptoContract.validate_base64url_key(:signature, 64)
  end

  defp validate_pre_keys_unique(changeset) do
    pre_keys = get_change(changeset, :pre_keys, [])
    key_ids = Enum.map(pre_keys, &get_change(&1, :key_id)) |> Enum.reject(&is_nil/1)

    if Enum.uniq(key_ids) == key_ids do
      changeset
    else
      add_error(changeset, :pre_keys, "key_id values must be unique within this upload")
    end
  end
end
