// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;

public class BlockChain {
  public static final int CUT_OFF_AGE = 10;

  // [Note]: UTXO is a per block data structure, but probably the blockchain
  // could be pruned using CUT_OFF_AGE.
  public class BlockWrapper {
    public Block block;
    public int height;
    public ArrayList<BlockWrapper> children;
    // UTXO pool for making a new block on top of this block.
    public UTXOPool utxo_pool;

    public BlockWrapper(Block block, int height, UTXOPool pool) {
      this.block = block;
      this.height = height;
      this.utxo_pool = pool;
      this.children = new ArrayList<BlockWrapper>();
    }

    // Make a copy in condition we don't want internal pool to be changed.
    public UTXOPool getUTXOPoolCopy() {
      return new UTXOPool(utxo_pool);
    }
  }

  private HashMap<ByteArrayWrapper, BlockWrapper> hash_to_block_;
  // Tracks the the common headers, used to prune BlockChain data structure
  // holding in memory.
  private ArrayList<BlockWrapper> headers_;
  private BlockWrapper max_height_block_wrapper_;
  private TransactionPool tx_pool_;

  /**
   * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
   * block
   */
  public BlockChain(Block genesisBlock) {
    hash_to_block_ = new HashMap<ByteArrayWrapper, BlockWrapper>();
    tx_pool_ = new TransactionPool();
    headers_ = new ArrayList<BlockWrapper>();

    Transaction genesis_coinbase_tx = genesisBlock.getCoinbase();
    UTXO u = new UTXO(genesis_coinbase_tx.getHash(), 0);
    UTXOPool pool = new UTXOPool();
    pool.addUTXO(u, genesis_coinbase_tx.getOutput(0));

    ByteArrayWrapper key = new ByteArrayWrapper(genesisBlock.getHash());
    BlockWrapper value = new BlockWrapper(genesisBlock, 1, pool);
    hash_to_block_.put(key, value);
    headers_.add(value);
    max_height_block_wrapper_ = value;
  }

  private int getMaxHeight() {
    return max_height_block_wrapper_.height;
  }

  /** Get the maximum height block */
  public Block getMaxHeightBlock() {
    return max_height_block_wrapper_.block;
  }

  /** Get the UTXOPool for mining a new block on top of max height block */
  public UTXOPool getMaxHeightUTXOPool() {
    // [Note]: We'd better make a copy since we don't know how caller will deal
    // with it; don't want to corrupt internal data structure.
    return max_height_block_wrapper_.getUTXOPoolCopy();
  }

  /** Get the transaction pool to mine a new block */
  public TransactionPool getTransactionPool() {
    return tx_pool_;
  }

  /**
   * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
   * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
   * 
   * <p>
   * For example, you can try creating a new block over the genesis block (block height 2) if the
   * block chain height is {@code <=
   * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
   * at height 2.
   * 
   * @return true if block is successfully added
   */
  public boolean addBlock(Block block) {
    // block claims to be a genesis block, returns false.
    if (block.getPrevBlockHash() == null) 
      return false;

    ByteArrayWrapper parent_hash = new ByteArrayWrapper(block.getPrevBlockHash());
    BlockWrapper parent_wrapper = hash_to_block_.get(parent_hash);
    // No such parent in blockchain - this maybe because:
    // 1. it is an invalid block;
    // 2. block is too old to be added into chain (its parent has gone...).
    if (parent_wrapper == null)
      return false;

    int proposed_height = parent_wrapper.height + 1;

// Don't actually need following check for this pruning solution - if this block
// cannot be added then its parent should have been removed and it won't enter
// here.
// [Note]: If proposed_height >= maxHeight - CUT_OFF_AGE + 1; could add.
// (Within CUT_OFF_AGE blocks away from max height block.)
//    if (proposed_height < getMaxHeight() - CUT_OFF_AGE + 1)
//      return false;

    // Build block's utxo pool on top of its parent's pool.
    // [Note]: TxHandler makes a copy of passed-in parent_pool.
    // Or: UTXOPool parent_pool = parent_wrapper.getUTXOPoolCopy();
    UTXOPool parent_pool = parent_wrapper.utxo_pool;
    TxHandler tx_handler = new TxHandler(parent_pool);
    Transaction[] block_txs = block.getTransactions().toArray(new Transaction[0]);
    Transaction[] handled_txs = tx_handler.handleTxs(block_txs);
    if (block_txs.length != handled_txs.length)
      return false;

    UTXOPool block_pool = tx_handler.getUTXOPool();
    // Deal with block's coinbase since it is not handled by handleTxs().
    Transaction block_coinbase_tx = block.getCoinbase();
    block_pool.addUTXO(new UTXO(block_coinbase_tx.getHash(), 0),
        block_coinbase_tx.getOutput(0));
    // Update this node's global TransactionPool.
    for (Transaction tx : block_txs) {
      tx_pool_.removeTransaction(tx.getHash());
    }

    BlockWrapper added_block_wrapper =
      new BlockWrapper(block, proposed_height, block_pool);
    hash_to_block_.put(new ByteArrayWrapper(block.getHash()),
        added_block_wrapper);
    // Update block's parent children list.
    parent_wrapper.children.add(added_block_wrapper);

    // Update the max height block if necessary. [Note] if multiple branches
    // have the same height then max height block is the oldest block is
    // implicitly guaranteed.
    if (proposed_height > getMaxHeight())
      max_height_block_wrapper_ = added_block_wrapper;

    // Prune BlockChain if necessary.
    // Pruning algorithm:
    ArrayList<BlockWrapper> new_headers = new ArrayList<BlockWrapper>(headers_);
    for (BlockWrapper header : headers_) {
      // [Note]: We could cut off header block if no future block is possible
      // to build upon it - i.e.:
      // if (header.height + 1) < maxHeight - CUT_OFF_AGE + 1, cut header block.
      if (header.height < getMaxHeight() - CUT_OFF_AGE) {
        for (BlockWrapper child : header.children) {
          // Cutted-off header's children would become new common headers
          // (Header of trees in the forest).
          new_headers.add(child);
        }
        hash_to_block_.remove(new ByteArrayWrapper(header.block.getHash()));
        new_headers.remove(header);
      }
    }
    headers_ = new_headers;

    return true;
  }

  /** Add a transaction to the transaction pool */
  public void addTransaction(Transaction tx) {
    tx_pool_.addTransaction(tx);
  }
}
