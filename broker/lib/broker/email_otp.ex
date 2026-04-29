defmodule Broker.EmailOTP do
  @moduledoc """
  Sends OTP verification emails via Gmail SMTP using Swoosh.
  Used for both email-based and phone-based authentication flows.
  """
  import Swoosh.Email
  alias Broker.Mailer

  @from_name "Aura Chat"

  defp from_email do
    Application.get_env(:broker, :otp_sender_email) || "aura.chat.verify@gmail.com"
  end

  @doc """
  Sends a 6-digit OTP verification email.
  """
  def send_otp(to_email, otp_code) do
    email =
      new()
      |> to(to_email)
      |> from({@from_name, from_email()})
      |> subject("Your Aura verification code: #{otp_code}")
      |> html_body(otp_html(otp_code))
      |> text_body(
        "Your Aura Chat verification code is: #{otp_code}\n\nThis code expires in 5 minutes. Do not share it with anyone."
      )

    case Mailer.deliver(email) do
      {:ok, _} ->
        :ok

      {:error, reason} ->
        require Logger
        Logger.error("Failed to send OTP email to #{to_email}: #{inspect(reason)}")
        {:error, reason}
    end
  end

  @doc """
  Sends a password reset email with a reset code.
  """
  def send_password_reset(to_email, reset_code) do
    email =
      new()
      |> to(to_email)
      |> from({@from_name, from_email()})
      |> subject("Password Reset Code: #{reset_code}")
      |> html_body(reset_html(reset_code))
      |> text_body(
        "Your password reset code is: #{reset_code}\n\nThis code expires in 15 minutes."
      )

    case Mailer.deliver(email) do
      {:ok, _} ->
        :ok

      {:error, reason} ->
        require Logger
        Logger.error("Failed to send reset email to #{to_email}: #{inspect(reason)}")
        {:error, reason}
    end
  end

  defp otp_html(code) do
    """
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 420px; margin: 0 auto; padding: 40px 24px; background: #0d0d2b; color: #ffffff; border-radius: 16px;">
      <div style="text-align: center; margin-bottom: 32px;">
        <h1 style="font-size: 24px; font-weight: 700; margin: 0; color: #ffffff;">🌌 Aura Chat</h1>
      </div>
      <p style="font-size: 16px; color: #ccccdd; margin-bottom: 8px;">Your verification code is:</p>
      <div style="background: linear-gradient(135deg, #6C5CE7, #a855f7); border-radius: 12px; padding: 24px; text-align: center; margin: 16px 0;">
        <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #ffffff;">#{code}</span>
      </div>
      <p style="font-size: 14px; color: #888899; margin-top: 24px;">This code expires in <strong>5 minutes</strong>.</p>
      <p style="font-size: 13px; color: #666677; margin-top: 16px;">If you didn't request this, you can safely ignore this email.</p>
      <hr style="border: none; border-top: 1px solid #222244; margin: 24px 0;" />
      <p style="font-size: 12px; color: #555566; text-align: center;">Aura Chat — End-to-End Encrypted Messaging</p>
    </div>
    """
  end

  defp reset_html(code) do
    """
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 420px; margin: 0 auto; padding: 40px 24px; background: #0d0d2b; color: #ffffff; border-radius: 16px;">
      <div style="text-align: center; margin-bottom: 32px;">
        <h1 style="font-size: 24px; font-weight: 700; margin: 0; color: #ffffff;">🔑 Aura Reset</h1>
      </div>
      <p style="font-size: 16px; color: #ccccdd; margin-bottom: 8px;">Your password reset code is:</p>
      <div style="background: linear-gradient(135deg, #e74c3c, #c0392b); border-radius: 12px; padding: 24px; text-align: center; margin: 16px 0;">
        <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #ffffff;">#{code}</span>
      </div>
      <p style="font-size: 14px; color: #888899; margin-top: 24px;">This code expires in <strong>15 minutes</strong>.</p>
      <p style="font-size: 13px; color: #666677; margin-top: 16px;">If you didn't request a password reset, please secure your account immediately.</p>
      <hr style="border: none; border-top: 1px solid #222244; margin: 24px 0;" />
      <p style="font-size: 12px; color: #555566; text-align: center;">Aura Chat — End-to-End Encrypted Messaging</p>
    </div>
    """
  end

  @doc """
  Sends an OTP email for phone-number-based signups.
  The email explains that the code is for verifying their Aura Chat account
  linked to their phone number.
  """
  def send_phone_otp(to_email, otp_code) do
    email =
      new()
      |> to(to_email)
      |> from({@from_name, from_email()})
      |> subject("Aura Chat — Verify your phone number: #{otp_code}")
      |> html_body(phone_otp_html(otp_code))
      |> text_body(
        "Your Aura Chat phone verification code is: #{otp_code}\n\nYou're receiving this because you signed up for Aura Chat with your phone number and provided this email for verification.\n\nThis code expires in 5 minutes. Do not share it with anyone."
      )

    case Mailer.deliver(email) do
      {:ok, _} ->
        :ok

      {:error, reason} ->
        require Logger
        Logger.error("Failed to send phone OTP email to #{to_email}: #{inspect(reason)}")
        {:error, reason}
    end
  end

  defp phone_otp_html(code) do
    """
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 420px; margin: 0 auto; padding: 40px 24px; background: #0d0d2b; color: #ffffff; border-radius: 16px;">
      <div style="text-align: center; margin-bottom: 32px;">
        <h1 style="font-size: 24px; font-weight: 700; margin: 0; color: #ffffff;">📱 Aura Chat</h1>
        <p style="font-size: 14px; color: #888899; margin-top: 8px;">Phone Number Verification</p>
      </div>
      <p style="font-size: 16px; color: #ccccdd; margin-bottom: 8px;">You signed up with your phone number. Enter this code to verify your account:</p>
      <div style="background: linear-gradient(135deg, #6C5CE7, #a855f7); border-radius: 12px; padding: 24px; text-align: center; margin: 16px 0;">
        <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #ffffff;">#{code}</span>
      </div>
      <p style="font-size: 14px; color: #888899; margin-top: 24px;">This code expires in <strong>5 minutes</strong>.</p>
      <p style="font-size: 13px; color: #666677; margin-top: 16px;">If you didn't sign up for Aura Chat, you can safely ignore this email.</p>
      <hr style="border: none; border-top: 1px solid #222244; margin: 24px 0;" />
      <p style="font-size: 12px; color: #555566; text-align: center;">Aura Chat — End-to-End Encrypted Messaging</p>
    </div>
    """
  end
end
