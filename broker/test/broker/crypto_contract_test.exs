defmodule Broker.CryptoContractTest do
  use ExUnit.Case
  import Ecto.Changeset
  alias Broker.CryptoContract

  defmodule DummySchema do
    use Ecto.Schema

    embedded_schema do
      field :key, :binary
      field :sig, :binary
    end
  end

  test "validate_base64url_key valid paddingless URL-safe base64" do
    # 32 bytes of zeros
    valid_key = Base.url_decode64!("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", padding: false)
    valid_b64 = Base.url_encode64(valid_key, padding: false)

    changeset = cast(%DummySchema{}, %{"key" => valid_b64}, [:key])
    changeset = CryptoContract.validate_base64url_key(changeset, :key, 32)

    assert changeset.valid?
    assert get_change(changeset, :key) == valid_key
  end

  test "validate_base64url_key invalid size" do
    invalid_b64 = Base.url_encode64(:crypto.strong_rand_bytes(31), padding: false)

    changeset = cast(%DummySchema{}, %{"key" => invalid_b64}, [:key])
    changeset = CryptoContract.validate_base64url_key(changeset, :key, 32)

    refute changeset.valid?
    assert %{key: ["invalid_format"]} = errors_on(changeset)
  end

  test "reject_private_key_hints detects typical private key headers" do
    changeset = cast(%DummySchema{}, %{"key" => "-----BEGIN PRIVATE KEY-----xyz"}, [:key])
    changeset = CryptoContract.reject_private_key_hints(changeset, [:key])

    refute changeset.valid?
    assert %{key: ["invalid_format"]} = errors_on(changeset)
  end

  def errors_on(changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Regex.replace(~r"%{(\w+)}", msg, fn _, key ->
        opts |> Keyword.get(String.to_existing_atom(key), key) |> to_string()
      end)
    end)
  end
end
