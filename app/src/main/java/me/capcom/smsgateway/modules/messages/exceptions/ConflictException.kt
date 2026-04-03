package me.capcom.smsgateway.modules.messages.exceptions

class ConflictException : RuntimeException("Message with the same ID already exists")