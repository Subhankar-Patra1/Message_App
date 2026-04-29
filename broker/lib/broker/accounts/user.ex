defmodule Broker.Accounts.User do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:id, :binary_id, autogenerate: true}
  @foreign_key_type :binary_id
  schema "users" do
    field :phone_hash, :binary
    field :email, :string
    field :password_hash, :string
    field :first_name, :string
    field :last_name, :string
    field :avatar_url, :string
    field :username, :string
    field :username_pin_hash, :string
    field :last_seen_at, :utc_datetime

    timestamps(type: :utc_datetime)
  end

  @doc false
  def changeset(user, attrs) do
    user
    |> cast(attrs, [:phone_hash, :email, :password_hash, :first_name, :last_name, :avatar_url, :last_seen_at])
    # Now that we support both phone and email, we shouldn't require phone_hash
    |> unique_constraint(:phone_hash)
    |> unique_constraint(:email)
  end

  def username_changeset(user, attrs) do
    user
    |> cast(attrs, [:username, :username_pin_hash])
    |> validate_required([:username])
    |> validate_length(:username, min: 3, max: 35)
    |> validate_format(:username, ~r/^[a-z0-9._]+$/, message: "can only contain lowercase letters, numbers, periods, and underscores")
    |> validate_format(:username, ~r/[a-z]/, message: "must contain at least one letter")
    |> validate_format(:username, ~r/^(?!www\.).*$/, message: "cannot start with www.")
    |> validate_format(:username, ~r/.*(?<!\.(com|net))$/, message: "cannot end with .com or .net")
    |> unique_constraint(:username)
  end
end
