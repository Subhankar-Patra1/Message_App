token = BrokerWeb.Token.generate_and_sign!("a", "b", ["c"])
IO.inspect(BrokerWeb.Token.verify_jwt(token))
