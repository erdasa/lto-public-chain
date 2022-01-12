from behave import *
from e2e.common.tools import *
from lto.transactions.register import Register


def register(context, user=None, key_type="ed25519", public_key=None, version=None):
    account = context.users[user] if user else ROOT_ACCOUNT
    register_account = {key_type, public_key}

    transaction = Register(register_account)
    transaction.version = version or Register.DEFAULT_VERSION
    transaction.sign_with(account)

    broadcast(context, transaction)


@when(u'{user} registers an account')
@when(u'{user} registers (v{version:d}) an account')
@when(u'{user} registers an account with public key "{public_key}"')
@when(u'{user} registers an account with {key_type} public key "{public_key}"')
@when(u'{user} registers (v{version:d}) an account with {key_type} public key "{public_key}"')
def step_impl(context, user, version=None, key_type='ed25519', public_key=None):
    register(context, user, key_type, public_key, version)


@when('{user} tries to register an account')
def step_impl(context, user):
    try:
        register(context, user)
    except:
        pass