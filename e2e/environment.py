from e2e.common import node
from e2e.common import tools
from behave.model_core import Status

def before_all(context):
    context.started_node = False
    if not node.is_node_up():
        node.start_node()
        context.started_node = True
        assert node.is_node_up(30), "Unable to connect to node"

def after_all(context):
    if context.started_node:
        node.stop_node()
        
def after_scenario(context, scenario):
    if (scenario.status == Status.failed):
        print_users()
        print_txs()
    tools.reset()

def print_users():
    if (tools.USERS):
        print('      Users:')
        
    for user, account in tools.USERS.items():
        print(f'        \033[1m\33[90m{user: <8}\33[0m\33[90m {account.address}\33[0m')

def print_txs():
    if (tools.transactions):
        print('      Transactions:')

    for txid in tools.transactions:
        print(f'        \33[90m{txid}\33[0m')

