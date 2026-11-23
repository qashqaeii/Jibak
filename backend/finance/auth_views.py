"""Rate-limited short-lived JWT sign-in for the native app alpha.

No registration or OTP is implied here; users must be provisioned by the service.
The Android client intentionally never stores refresh tokens or passwords.
"""
from rest_framework.throttling import AnonRateThrottle
from rest_framework_simplejwt.views import TokenObtainPairView


class LoginThrottle(AnonRateThrottle):
    rate = "5/minute"


class AiMobileTokenView(TokenObtainPairView):
    throttle_classes = [LoginThrottle]
