defmodule BrokerWeb.Token do
  use Joken.Config

  # Configure your token's default claims
  def token_config do
    # 365 days — never force re-login (WhatsApp-style)
    default_claims(skip: [:aud, :iss], default_exp: 60 * 60 * 24 * 365)
    |> add_claim("sub", nil, &(&1 != nil))
    |> add_claim("dev", nil, &(&1 != nil))
    |> add_claim("scopes", nil, &(&1 != nil))
  end

  # Function to generate a new token
  def generate_and_sign!(user_id, device_id, scopes) do
    claims = %{
      "sub" => user_id,
      "dev" => device_id,
      "scopes" => scopes
    }

    {:ok, token, _claims} = generate_and_sign(claims, signer())
    token
  end

  def verify_jwt(token) do
    verify_and_validate(token, signer())
  end

  defp signer do
    secret = Application.get_env(:broker, :jwt_secret)
    Joken.Signer.create("HS256", secret)
  end
end
