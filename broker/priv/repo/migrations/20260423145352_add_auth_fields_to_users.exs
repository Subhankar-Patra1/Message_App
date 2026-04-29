defmodule Broker.Repo.Migrations.AddAuthFieldsToUsers do
  use Ecto.Migration

  def change do
    alter table(:users) do
      add :email, :string
      add :password_hash, :string
      modify :phone_hash, :binary, null: true, from: {:binary, null: false}
    end

    create unique_index(:users, [:email])
  end
end
