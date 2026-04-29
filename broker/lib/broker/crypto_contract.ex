defmodule Broker.CryptoContract do
  import Ecto.Changeset
  require Logger

  @doc """
  Validates a field as base64url-no-pad and checks for exact byte size.
  Decodes and replaces the field value with the raw binary if valid.
  """
  def validate_base64url_key(changeset, field, expected_bytes) do
    validate_change(changeset, field, fn ^field, value ->
      case Base.url_decode64(value, padding: false) do
        {:ok, decoded} when byte_size(decoded) == expected_bytes ->
          []

        {:ok, decoded} ->
          log_validation_failure(field, value, expected_bytes, byte_size(decoded))
          [{field, "invalid_format"}]

        :error ->
          log_validation_failure(field, value, expected_bytes, "invalid base64url")
          [{field, "invalid_format"}]
      end
    end)
    |> decode_if_valid(field)
  end

  defp decode_if_valid(changeset, field) do
    if get_error(changeset, field) == nil do
      case get_change(changeset, field) do
        nil ->
          changeset

        value when is_binary(value) ->
          # We only decode if it hasn't been decoded already
          case Base.url_decode64(value, padding: false) do
            {:ok, decoded} -> put_change(changeset, field, decoded)
            _ -> changeset
          end

        _ ->
          changeset
      end
    else
      changeset
    end
  end

  defp get_error(changeset, field) do
    Enum.find(changeset.errors, fn {f, _} -> f == field end)
  end

  @doc """
  Scans specified fields for obvious private key material patterns.
  """
  def reject_private_key_hints(changeset, fields) do
    Enum.reduce(fields, changeset, fn field, acc ->
      case get_change(acc, field) do
        nil ->
          acc

        value when is_binary(value) ->
          if String.contains?(value, ["-----BEGIN", "PRIVATE KEY", "EC PRIVATE"]) do
            # Log as security warning
            Logger.warning("Detected potential private key material in upload",
              error_field: field,
              value_hash: hash_value(value)
            )

            add_error(acc, field, "invalid_format")
          else
            acc
          end

        _ ->
          acc
      end
    end)
  end

  defp log_validation_failure(field, raw_value, expected, actual) do
    Logger.warning("Crypto contract validation failed",
      error_field: field,
      expected_bytes: expected,
      actual: actual,
      value_hash: hash_value(raw_value)
    )
  end

  defp hash_value(value) when is_binary(value) do
    :crypto.hash(:sha256, value) |> Base.encode16(case: :lower)
  end

  defp hash_value(_), do: "not_a_string"
end
