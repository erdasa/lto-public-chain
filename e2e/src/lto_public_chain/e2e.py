import hashlib
from time import sleep
import unittest

import base58

import api
import api_external
import http_requests
import utils


class E2eTests(unittest.TestCase):
    validator = api.create_account(
        'cable sniff exchange judge gym rifle prevent traffic picture firm shaft exist cute unusual check')
    alice = api.create_account(
        'amazing use view color endless fever dinner corn sight history lobster sniff merit fly evolve')
    bob = api.create_account(
        'home visit certain universe adjust thing estate pyramid age puzzle update ensure fatal crucial hat')
    charlie = api.create_account(
        'amazing use view color endless fever dinner corn sight history lobster sniff merit fly sight')

    TRANSFER_FEE = 100000000
    MASS_TRANSFER_FEE_PER_TX = 10000000
    SPONSOR_FEE = 500000000

    def test_connectivity(self):
        self.assertEqual(
            http_requests.get("/").status_code,
            200)

    # Scenario:
    # 1. Alice gets associated with Bob
    # 2. Alice revokes her association with Bob
    def test_association(self):
        anchor = utils.random_string()

        # Step 1: Alice gets associated with Bob
        invoke_assoc_tx = api.invoke_association(self.alice, self.bob, anchor)
        invoke_assoc_tx_id = invoke_assoc_tx.id
        polled_invoke_assoc_tx = api.get_tx_polled(invoke_assoc_tx_id)

        self.assertEqual(
            polled_invoke_assoc_tx['id'],
            invoke_assoc_tx_id)

        alice_assocs = api.list_associations(self.alice.address).json()
        alice_assoc = next(
            (assoc for assoc in alice_assocs['outgoing'] if assoc['issueTransactionId'] == invoke_assoc_tx_id), None)

        self.assertIsNotNone(alice_assoc)
        self.assertEqual(
            alice_assoc['associationType'],
            1
        )
        self.assertEqual(
            alice_assoc['party'],
            self.bob.address
        )
        self.assertFalse('revokeTransactionId' in alice_assoc)
        self.assertFalse('revokeHeight' in alice_assoc)

        bob_assocs = api.list_associations(self.bob.address).json()
        bob_assoc = next(
            (assoc for assoc in bob_assocs['incoming'] if assoc['issueTransactionId'] == invoke_assoc_tx_id), None)

        self.assertIsNotNone(bob_assoc)
        self.assertEqual(
            bob_assoc['associationType'],
            1
        )
        self.assertEqual(
            bob_assoc['party'],
            self.alice.address
        )
        self.assertEqual(
            polled_invoke_assoc_tx['id'],
            invoke_assoc_tx.id)
        self.assertFalse('revokeTransactionId' in bob_assoc)
        self.assertFalse('revokeHeight' in bob_assoc)

        # Step 2: Alice revokes her association with Bob
        revoke_assoc_tx = api.revoke_association(self.alice, self.bob, anchor)
        revoke_assoc_tx_id = revoke_assoc_tx.id
        polled_revoke_assoc_tx = api.get_tx_polled(revoke_assoc_tx_id)

        self.assertEqual(
            polled_revoke_assoc_tx['id'],
            revoke_assoc_tx_id)

        alice_assocs_after_revoke = api.list_associations(self.alice.address).json()
        alice_assoc_after_revoke = next((assoc for assoc in alice_assocs_after_revoke['outgoing'] if
                                         'revokeTransactionId' in assoc and assoc[
                                             'revokeTransactionId'] == revoke_assoc_tx_id), None)

        self.assertIsNotNone(alice_assoc_after_revoke)
        self.assertEqual(
            alice_assoc_after_revoke['issueTransactionId'],
            alice_assoc['issueTransactionId']
        )
        self.assertEqual(
            alice_assoc_after_revoke['associationType'],
            alice_assoc['associationType']
        )
        self.assertEqual(
            alice_assoc_after_revoke['party'],
            self.bob.address
        )
        self.assertTrue(alice_assoc_after_revoke['revokeHeight'] >= alice_assoc_after_revoke['issueHeight'])

        bob_assocs_after_revoke = api.list_associations(self.bob.address).json()
        bob_assoc_after_revoke = next((assoc for assoc in bob_assocs_after_revoke['incoming'] if
                                       'revokeTransactionId' in assoc and assoc[
                                           'revokeTransactionId'] == revoke_assoc_tx_id), None)

        self.assertIsNotNone(bob_assoc)
        self.assertEqual(
            bob_assoc_after_revoke['associationType'],
            1
        )
        self.assertEqual(
            bob_assoc_after_revoke['party'],
            self.alice.address
        )
        self.assertEqual(
            polled_invoke_assoc_tx['id'],
            invoke_assoc_tx.id)

    # Scenario:
    # 1. Alice leases LTO to the validator node
    # 2. Validator node goes down
    # 3. Alice withdraws her lease from the validator
    # 4. Validator node goes up
    # 5. Alice leases LTO to the validator node
    def test_lease(self):
        # Step 1: Alice leases LTO to the validator node
        lease_tx_id = self.lease_and_verify()

        # FIXME: Uncomment when manage to run a node in local custom network
        # Step 2: Validator node goes down
        # api.shutdown_node()
        # self.assertTrue(api.is_node_down())

        # Step 3: Alice withdraws her lease from the validator
        cancel_lease_tx_id = api_external.cancel_lease(self.alice, lease_tx_id).id
        polled_cancel_lease_tx = api_external.get_tx_polled(cancel_lease_tx_id)

        self.assertEqual(
            polled_cancel_lease_tx['id'],
            cancel_lease_tx_id)

        self.assertTrue(api_external.is_lease_missing(self.alice.address, lease_tx_id))

        self.assertTrue(api_external.is_lease_missing(self.validator.address, lease_tx_id))

        # FIXME: Uncomment when manage to run a node in local custom network
        # Step 4: Validator node goes up
        # api.start_node()
        # self.assertTrue(api.is_node_up())

        # Step 5: Alice leases LTO to the validator node
        self.lease_and_verify()

    def lease_and_verify(self):
        amount = 50000
        balance_before = api.get_address_balance(self.alice.address).json()

        lease_tx = api.lease(self.alice, self.validator.address, amount)
        lease_tx_id = lease_tx.id
        polled_lease_tx = api.get_tx_polled(lease_tx_id)

        self.assertEqual(
            polled_lease_tx['id'],
            lease_tx_id)

        alice_leases = api.list_active_leases(self.alice.address)
        alice_lease = next((lease for lease in alice_leases if lease['id'] == lease_tx_id), None)

        self.assertIsNotNone(alice_lease)
        self.assertEqual(
            alice_lease['amount'],
            amount
        )
        self.assertEqual(
            alice_lease['recipient'],
            self.validator.address
        )

        balance_after = api.get_address_balance(self.alice.address).json()

        self.assertEqual(
            balance_after['regular'],
            balance_before['regular'] - self.TRANSFER_FEE
        )

        self.assertEqual(
            balance_after['available'],
            balance_before['available'] - self.TRANSFER_FEE - amount
        )

        validator_leases = api.list_active_leases(self.validator.address)
        validator_lease = next((lease for lease in validator_leases if lease['id'] == lease_tx_id), None)

        self.assertIsNotNone(validator_lease)
        self.assertEqual(
            validator_lease['amount'],
            amount
        )
        self.assertEqual(
            validator_lease['sender'],
            self.alice.address
        )
        return lease_tx_id

    # Scenario:
    # 0. Validator gets rewarded with LTO
    # 1. Validator sends LTO to Alice, Bob and Charlie
    def test_mass_transfer(self):
        # Step 1: Validator sends LTO to Alice, Bob and Charlie
        validator_balance_before = api.get_address_balance(self.validator.address).json()['regular']
        alice_balance_before = api.get_address_balance(self.alice.address).json()['regular']
        bob_balance_before = api.get_address_balance(self.bob.address).json()['regular']
        charlie_balance_before = api.get_address_balance(self.charlie.address).json()['regular']
        amount = 50000

        transfers = [
            {
                'recipient': self.alice.address,
                'amount': amount
            },
            {
                'recipient': self.bob.address,
                'amount': amount
            },
            {
                'recipient': self.charlie.address,
                'amount': amount
            }
        ]

        tx = api.mass_transfer(self.validator, transfers)
        polled_tx = api.get_tx_polled(tx.id)

        self.assertEqual(
            polled_tx['id'],
            tx.id)

        self.assertEqual(
            validator_balance_before - (self.TRANSFER_FEE + 3 * (self.MASS_TRANSFER_FEE_PER_TX + amount)),
            api.get_address_balance(self.validator.address).json()['regular'])

        self.assertEqual(
            alice_balance_before + amount,
            api.get_address_balance(self.alice.address).json()['regular'])

        self.assertEqual(
            bob_balance_before + amount,
            api.get_address_balance(self.bob.address).json()['regular'])

        self.assertEqual(
            charlie_balance_before + amount,
            api.get_address_balance(self.charlie.address).json()['regular'])

    # Scenario:
    # 1. Charlie sponsors Alice
    # 2. Alice makes transfer to Bob, Charlie pays for the transfer costs
    # 3. Charlie revokes as sponsor of Alice
    # 4. Alice makes transfer to Bob, Alice pays for the transfer costs
    def test_sponsorship(self):
        # Step 1: Charlie sponsors Alice
        charlie_balance_before_1 = api.get_address_balance(self.charlie.address).json()['regular']
        sponsor_tx = api.sponsor(self.charlie, self.alice.address)
        polled_sponsor_tx = api.get_tx_polled(sponsor_tx.id)

        self.assertEqual(
            polled_sponsor_tx['id'],
            sponsor_tx.id)

        self.assertEqual(
            charlie_balance_before_1 - self.SPONSOR_FEE,
            api.get_address_balance(self.charlie.address).json()['regular'])

        # Step 2: Alice makes transfer to Bob, Charlie pays for the transfer costs
        amount = 10000
        charlie_balance_before_2 = api.get_address_balance(self.charlie.address).json()['regular']
        alice_balance_before_2 = api.get_address_balance(self.alice.address).json()['regular']
        bob_balance_before_2 = api.get_address_balance(self.bob.address).json()['regular']

        transfer_tx = api.transfer(self.alice, self.bob.address, 10000)
        polled_transfer_tx = api.get_tx_polled(transfer_tx.id)

        self.assertEqual(
            transfer_tx.id,
            polled_transfer_tx['id'])

        self.assertEqual(
            alice_balance_before_2 - amount,
            api.get_address_balance(self.alice.address).json()['regular'])

        self.assertEqual(
            bob_balance_before_2 + amount,
            api.get_address_balance(self.bob.address).json()['regular'])

        self.assertEqual(
            charlie_balance_before_2 - self.TRANSFER_FEE,
            api.get_address_balance(self.charlie.address).json()['regular'])

        # 3. Charlie revokes as sponsor of Alice
        charlie_balance_before_3 = api.get_address_balance(self.charlie.address).json()['regular']
        sponsor_tx = api.cancel_sponsor(self.charlie, self.alice.address)
        polled_sponsor_tx = api.get_tx_polled(sponsor_tx.id)

        self.assertEqual(
            polled_sponsor_tx['id'],
            sponsor_tx.id)

        self.assertEqual(
            charlie_balance_before_3 - self.SPONSOR_FEE,
            api.get_address_balance(self.charlie.address).json()['regular'])

        # Step 4: Alice makes transfer to Bob, Alice pays for the transfer costs
        amount = 10000
        charlie_balance_before_4 = api.get_address_balance(self.charlie.address).json()['regular']
        alice_balance_before_4 = api.get_address_balance(self.alice.address).json()['regular']
        bob_balance_before_4 = api.get_address_balance(self.bob.address).json()['regular']

        transfer_tx = api.transfer(self.alice, self.bob.address, 10000)
        polled_transfer_tx = api.get_tx_polled(transfer_tx.id)

        self.assertEqual(
            transfer_tx.id,
            polled_transfer_tx['id'])

        self.assertEqual(
            alice_balance_before_4 - amount - self.TRANSFER_FEE,
            api.get_address_balance(self.alice.address).json()['regular'])

        self.assertEqual(
            bob_balance_before_4 + amount,
            api.get_address_balance(self.bob.address).json()['regular'])

        self.assertEqual(
            charlie_balance_before_4,
            api.get_address_balance(self.charlie.address).json()['regular'])

    # Scenario:
    # 1. Alice anchors data
    # 2. Bob (or anyone) validates the data isn't tempered 
    def test_anchor(self):
        # Step 1: Alice anchors data
        anchor = 'e2etests'
        anchor_hashed = hashlib.sha256(str(anchor).encode('utf-8')).hexdigest()
        tx = api.anchor(self.alice, anchor_hashed)

        # Step 2: Bob (or anyone) validates the data isn't tampered
        polled_tx = api.get_tx_polled(tx.id)

        self.assertEqual(
            polled_tx['id'],
            tx.id)

        self.assertEqual(
            len(polled_tx['anchors']),
            1)

        self.assertEqual(
            base58.b58decode(polled_tx['anchors'][0]).decode('utf-8'),
            hashlib.sha256(str(anchor).encode('utf-8')).hexdigest())

    # Scenario1:
    # 1. Create and setup smart contract for Charlie
    # 2. Alice funds Charlie
    # 3. Alice can't take money from Charlie
    # 4.1 Bob takes funds because he knows secret hash and 4.2 after rollback wait height and Alice takes funds back
    # TODO
    def test_atomic_swap(self):
        # Step 1: Alice transfer 0.1 LTO to Bob
        amount = 10000000
        '''
        transfer_tx = api.transfer(self.alice, self.bob.address, amount)
        polled_transfer_tx = api.get_tx_polled(transfer_tx.id)
        self.assertEqual(polled_transfer_tx['id'], transfer_tx.id)

        # Step 2: Alice set up a smart contract preventing her from making transactions
        script = "match tx { \n" \
                 "case t:  TransferTransaction => false \n" \
                 "case _ => sigVerify(tx.bodyBytes, tx.proofs[0], tx.senderPublicKey)}"
        script_tx = api.set_script(self.alice, script)
        polled_script_tx = api.get_tx_polled(script_tx.id)
        self.assertEqual(polled_script_tx['id'], script_tx.id)'''

        # Step3: Alice cannot make transfer transaction anymore but can still do other transactions
        '''with self.assertRaises(Exception):
            api.transfer(self.alice, self.bob.address, amount)'''



        anchor_hashed = hashlib.sha256(str('anchor').encode('utf-8')).hexdigest()
        anchor_tx = api.anchor(self.alice, anchor_hashed)
        polled_anchor_tx = api.get_tx_polled(anchor_tx.id)
        self.assertEqual(polled_anchor_tx['id'], anchor_tx.id)



    def test_v3(self):
        # Anchor
        anchor = 'e2etests'
        anchor_hashed = hashlib.sha256(str(anchor).encode('utf-8')).hexdigest()
        anchor_tx = api.anchor(self.alice, anchor_hashed)

        polled_anchor_tx = api.get_tx_polled(anchor_tx.id)

        self.assertEqual(
            polled_anchor_tx['id'],
            anchor_tx.id)

        # Transfer
        transfer_tx = api.transfer(self.alice, self.bob.address, 1)

        polled_transfer_tx = api.get_tx_polled(transfer_tx.id)

        self.assertEqual(
            polled_transfer_tx['id'],
            transfer_tx.id)

        # Lease
        lease_tx = api.lease(self.alice, self.bob.address, 1)

        polled_lease_tx = api.get_tx_polled(lease_tx.id)

        self.assertEqual(
            polled_lease_tx['id'],
            lease_tx.id)

        # Cancel Lease
        cancel_lease_tx = api.cancel_lease(self.alice, lease_tx.id)

        polled_cancel_lease_tx = api.get_tx_polled(cancel_lease_tx.id)

        self.assertEqual(
            polled_cancel_lease_tx['id'],
            cancel_lease_tx.id)

        # Sponsor
        sponsor_tx = api.sponsor(self.alice, self.bob.address)

        polled_sponsor_tx = api.get_tx_polled(sponsor_tx.id)

        self.assertEqual(
            polled_sponsor_tx['id'],
            sponsor_tx.id)

        # Cancel Sponsor
        cancel_sponsor_tx = api.cancel_sponsor(self.alice, self.bob.address)

        polled_cancel_sponsor_tx = api.get_tx_polled(cancel_sponsor_tx.id)

        self.assertEqual(
            polled_cancel_sponsor_tx['id'],
            cancel_sponsor_tx.id)


def run():
    suite = unittest.TestSuite()
    suite.addTest(E2eTests("test_connectivity"))
    suite.addTest(E2eTests("test_association"))
    suite.addTest(E2eTests("test_lease"))
    suite.addTest(E2eTests("test_mass_transfer"))
    suite.addTest(E2eTests("test_sponsorship"))
    suite.addTest(E2eTests("test_anchor"))
    suite.addTest(E2eTests("test_atomic_swap"))
    suite.addTest(E2eTests("test_v3"))
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    assert result.wasSuccessful()


if __name__ == "__main__":
    run()
