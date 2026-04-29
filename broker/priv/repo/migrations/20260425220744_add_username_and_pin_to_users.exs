defmodule Broker.Repo.Migrations.AddUsernameAndPinToUsers do
  use Ecto.Migration

  def change do
    alter table(:users) do
      add :username, :string
      add :username_pin_hash, :string
    end

    create unique_index(:users, [:username])
  end
end
