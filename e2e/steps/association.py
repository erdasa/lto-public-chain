import lto
from behave import *
from e2e.common.tools import *


@given('{sender} has an association with {recipient} of type {type:d}')
@given('{sender} has an association with {recipient} of type {type:d} and anchor {hash}')
def step_impl(context, sender, recipient, type, hash=""):
    if not is_associated(sender, recipient):
        funds_for_transaction(sender, lto.Association.DEFAULT_LEASE_FEE)
        association(sender, recipient, type, hash)
        assert is_associated(sender, recipient), 'Failed to issue association'

@given('{sender} does not have an association with {recipient} of type {type:d}')
def step_impl(context, sender, recipient, type):
    if is_associated(sender, recipient):
        funds_for_transaction(sender, lto.RevokeAssociation.DEFAULT_LEASE_FEE)
        revoke_association(sender, recipient, type, hash)
        assert revoke_association(sender, recipient), 'Failed to revoke association'

@when('{sender} issues an association with {recipient} of type {type:d}')
@when('{sender} issues an association (v{version:d}) with {recipient} of type {type:d}')
def step_impl(context, sender, recipient, type, version=None):
    association(sender, recipient, type, version=version)

@when('{sender} revokes the association with {recipient} of type {type:d}')
@when('{sender} revokes the association (v{version:d}) with {recipient} of type {type:d}')
def step_impl(context, sender, recipient, type, version=None):
    revoke_association(sender, recipient, type, version=version)

@when('{sender} revokes the association with {recipient} of type {type:d} and anchor {hash}')
def step_impl(context, sender, recipient, type, hash):
    revoke_association(sender, recipient, type, hash)

@when(u'{sender} tries to issue an association with {recipient} of type {type:d}')
def step_impl(context, sender, recipient, type):
    try:
        association(sender, recipient, type)
    except:
        pass

@when(u'{sender} tries to revoke an association with {recipient} of type {type:d}')
def step_impl(context, sender, recipient, type):
    try:
        revoke_association(sender, recipient, type)
    except:
        pass


@then('{sender} is associated with {recipient}')
def step_impl(context, sender, recipient):
    assert_that(is_associated(sender, recipient))


@then('{sender} is not associated with {recipient}')
def step_impl(context, sender, recipient):
    assert_that(not is_associated(sender, recipient))
