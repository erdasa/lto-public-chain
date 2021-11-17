import lto
from behave import *
import tools


@given('{user1} is not sponsoring {user2}')
def step_impl(context, user1, user2):
    try:
        assert tools.is_sponsoring(user1, user2) == False
    except:
        tools.funds_for_transaction(user1, lto.CancelSponsorship.DEFAULT_SPONSORSHIP_FEE)
        tools.cancel_sponsorship(user2, user1)

@given('{user1} is sponsoring {user2}')
def step_impl(context, user1, user2):
    try:
        assert tools.is_sponsoring(user1, user2) == True
    except:
        tools.funds_for_transaction(user1, lto.Sponsorship.DEFAULT_SPONSORSHIP_FEE)
        tools.sponsor(user2, user1)


@when('{user1} tries to sponsor {user2}')
def step_impl(context, user1, user2):
    try:
        tools.sponsor(user2, user1)
    except:
        pass

@when('{user1} tries to cancel the sponsorship for {user2}')
def step_impl(context, user1, user2):
    try:
        tools.cancel_sponsorship(user2, user1)
    except:
        pass

@when('{user1} sponsors {user2}')
@when('{user1} sponsors (v{version:d}) {user2}')
def step_impl(context, user1, user2, version=None):
    tools.sponsor(user2, user1, version)


@when('{user1} cancels the sponsorship for {user2}')
@when('{user1} cancels the sponsorship (v{version:d}) for {user2}')
def step_impl(context, user1, user2, version=None):
    tools.cancel_sponsorship(user2, user1, version)


@then('{user1} is sponsoring {user2}')
def step_impl(context, user1, user2):
    assert tools.is_sponsoring(user1, user2) is True


@then('{user1} is not sponsoring {user2}')
def step_impl(context, user1, user2):
    assert tools.is_sponsoring(user1, user2) is False
