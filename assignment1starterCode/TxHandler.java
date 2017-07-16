import java.util.*;

public class TxHandler {
  protected UTXOPool utxo_pool_;

  /*
   * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
   * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
   * constructor.
   */
  public TxHandler(UTXOPool utxoPool) {
    utxo_pool_ = new UTXOPool(utxoPool);
  }

  /*
   * @return true if:
   * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
   * (2) the signatures on each input of {@code tx} are valid, 
   * (3) no UTXO is claimed multiple times by {@code tx},
   * (4) all of {@code tx}s output values are non-negative, and
   * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
   *     values; and false otherwise.
   *  Note: This api assumes that tx.inputs all consume from utxo pool.
   *  
   *  [Additional notes]:
   *  Say, A send X coins to B. A must have some unspent coins - in UTXOPool,
   *  say utxo(prevTx.hash, index into prevTx's outputs). And A needs to know
   *  B's account address (B's public_key), and the proposed transaction tx
   *  would be like:
   *  {
   *    Input[0] - <prevTx's hash, index into prevTx's outputs, sig (this one needs to be generated, see below)>
   *    Output[0] - <value, B's address>
   *  }
   *  So the signature A generates and puts into tx.inputs[0].sig would be:
   *    sig <---- Sign(A's private_key, tx.getRawDataToSign(0))
   *  And B could verify this signature by: Verify(A's public_key, sig, tx.getRawDataToSign(0)),
   *  where A's public_key could be fetched from utxo_pool_.getTxOutput(utxo).
   */
  public boolean isValidTx(Transaction tx) {
    HashSet<UTXO> consumed_utxo = new HashSet<UTXO>();
    double in_value_sum = 0.0;
    ArrayList<Transaction.Input> inputs = tx.getInputs();
    for (int index = 0; index < inputs.size(); ++index) {
      Transaction.Input in = inputs.get(index);
      UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
      // 1. All *consumed* outputs (which is TX.inputs) in this TX should come
      // from UTXOPool.
      if (!utxo_pool_.contains(u)) {
        return false;
      }
      // UXTO's corresponding Output.
      Transaction.Output consumed_output = utxo_pool_.getTxOutput(u);
      // Note: Though I think this should not happen if control reaches here.
      if (consumed_output == null) {
        return false;
      }
      in_value_sum += consumed_output.value;
      // 2. Verify signature of each input is valid.
      if (!Crypto.verifySignature(consumed_output.address,
                                  tx.getRawDataToSign(index),
                                  in.signature)) {
        return false;
      }
      // 3. No UTXO is consumed by this TX multiple times.
      if (consumed_utxo.contains(u)) {
        return false;
      }
      consumed_utxo.add(u);
    }
    // 4. All of TX's output value is non-negative. 
    double out_value_sum = 0.0;
    for (Transaction.Output out : tx.getOutputs()) {
      if (out.value < 0) {
        return false;
      }
      out_value_sum += out.value;
    }
    // 5. Sum of TX's input values >= sum of TX's output values.
    return in_value_sum >= out_value_sum;
  }

  // Checks if all tx.inputs consume from utxo pool. If not, it means tx may
  // depend on another txx's output, where txx and tx are in the same block.
  private boolean isTxConsumeAllFromUTXOPool(Transaction tx) {
    for (Transaction.Input in : tx.getInputs()) {
      UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
      if (!utxo_pool_.contains(u)) {
        return false;
      }
    }
    return true;
  }

  // Note: This assumes that tx consumes all from utxo pool.
  private void updateUTXOPool(Transaction tx) {
    for (Transaction.Input in : tx.getInputs()) {
      utxo_pool_.removeUTXO(new UTXO(in.prevTxHash, in.outputIndex));
    }
    int index = 0;
    for (Transaction.Output out : tx.getOutputs()) {
      utxo_pool_.addUTXO(new UTXO(tx.getHash(), index), out);
      index++;
    }
  }

  /*
   * Handles each epoch by receiving an unordered array of proposed transactions, checking each
   * transaction for correctness, returning a mutually valid array of accepted transactions, and
   * updating the current UTXO pool as appropriate.
   * 
   * [Note1]: A Transaction can reference another in the same block.
   *       More than one transaction may spend the same UTXO (double spend).
   * "Your implementation of handleTxs() should return a mutually valid
   *  transaction set of maximal size (one that can’t be enlarged simply by
   *  adding more transactions). It need not compute a set of maximum size
   *  (one for which there is no larger mutually valid transaction set)."
   * 
   * [Note2]: If two transactions have the double-spending problem, according to
   * the instructor - "In the real bitcoin world one of the transactions it
   * taken as valid and one is discarded. Which one is discarded is up to the
   * miner. But a clever miner would normally choose the one that resulted in
   * the highest transaction fees. This is the point of the optional
   * MaxFeeTxHandler assignment."
   *
   * [Note3]: If TX B references TX A's output, then TX A's inputs must all
   * consume from utxo pool, otherwise we should skip A and thus B also
   * (both invalid). Then we could process A first, update utxo pool, and at
   * this time B should consume all from utxo pool (A's unspent output) instead
   * of referencing TX A - this simplifies problems.
   * We could always process those TXs that consume all from utxo pool,
   * update the pool, and keep iterating until utxo pool cannot be updated
   * any more.
   */
  public Transaction[] handleTxs(Transaction[] possibleTxs) {
    ArrayList<Transaction> tx_result = new ArrayList<Transaction>();
    ArrayList<Transaction> working_txs =
      new ArrayList<Transaction>(Arrays.asList(possibleTxs));
    boolean changed = false;
    ArrayList<Transaction> valid_tx_consume_all_from_pool =
      new ArrayList<Transaction>();
    ArrayList<Transaction> tx_not_consume_all_from_pool =
      new ArrayList<Transaction>();

    while (true) {
      for (Transaction tx : working_txs) {
        if (isTxConsumeAllFromUTXOPool(tx)) {
          if (isValidTx(tx)) {
            valid_tx_consume_all_from_pool.add(tx);
            updateUTXOPool(tx);
            changed = true;
          }
        } else {
          tx_not_consume_all_from_pool.add(tx);
        }
      }
      // Iterate until no change ever happenes to the utxo pool.
      if (!changed) {
        break;
      } else {
        tx_result.addAll(valid_tx_consume_all_from_pool);
        working_txs.clear();
        working_txs.addAll(tx_not_consume_all_from_pool);
        valid_tx_consume_all_from_pool.clear();
        tx_not_consume_all_from_pool.clear();
        changed = false;
      }
    }
    // Not toArray(), should use typed API - public <T> T[] toArray(T[] a).
    // https://stackoverflow.com/questions/4042434/converting-arrayliststring-to-string-in-java.
    return tx_result.toArray(new Transaction[tx_result.size()]);
  }
}
