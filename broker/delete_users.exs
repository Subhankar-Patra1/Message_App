import Ecto.Query
alias Broker.Repo
alias Broker.Accounts.User
alias Broker.Accounts.Device
alias Broker.Keys.PreKey
alias Broker.Keys.SignedPreKey

keep_emails = []

# Find users to delete
users_to_delete = Repo.all(from u in User, where: u.email not in ^keep_emails)

IO.puts "Deleting #{length(users_to_delete)} users..."

Enum.each(users_to_delete, fn user ->
  IO.puts "Deleting user: #{user.email || user.id}"
  
  # 1. Find devices
  device_ids = Repo.all(from d in Device, where: d.user_id == ^user.id, select: d.id)
  
  if device_ids != [] do
    # 2. Delete keys
    Repo.delete_all(from p in PreKey, where: p.device_id in ^device_ids)
    Repo.delete_all(from s in SignedPreKey, where: s.device_id in ^device_ids)
    # 3. Delete devices
    Repo.delete_all(from d in Device, where: d.user_id == ^user.id)
  end
  
  # 4. Delete user
  Repo.delete!(user)
end)

IO.puts "Cleanup complete."
