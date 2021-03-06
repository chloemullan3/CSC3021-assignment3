/*
 * BucketListMap.java
 *
 * Created on December 30, 2005, 3:24 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */

import java.util.concurrent.atomic.*;

/**
 * @param T item type
 * @author Maurice Herlihy
 */
public class BucketListMap<K, V> implements Map<K, V> {private Node head;
	static public final int WORD_SIZE = 23;
	static private final int LO_BIT = 1;
	static private final int HI_BIT = 1 << (WORD_SIZE - 1);
	static private final int REG_BIT = 1 << WORD_SIZE;
	static private final int PRE_MASK = (1 << (WORD_SIZE - 1)) - 1;

	private class Node {
		int hash;
		AtomicMarkableReference<Node> next;
		K key;
		V value;

		Node( int hash ) {
			this.hash = hash;
			next = new AtomicMarkableReference<Node>(null, false);

		}
		Node( int hash, K key, V value ) {
			this.hash = hash;
			this.key = key;
			this.value = value;
			next = new AtomicMarkableReference<Node>(null, false);
		}
	}
	private class Window {
		public Node pred;
		public Node curr;
		Window(Node pred, Node curr) {
			this.pred = pred;
			this.curr = curr;
		}
	}

	private Window find( Node head, int hash ) {
		Node pred = null;
		Node curr = null;
		Node succ = null;
		boolean[] marked = {false};
		boolean snip;
		retry: while( true ) {
			pred = head;
			curr = pred.next.getReference();
			while( true ) {
				succ = curr.next.get(marked);
				while(marked[0]) {
					snip = pred.next.compareAndSet(curr, succ, false, false);
					if(!snip) continue retry;
					curr = succ;
					succ = curr.next.get(marked);
				}
				if( curr.hash >= hash)
					return new Window( pred, curr );
				pred = curr;
				curr = succ;
			}
		}
	}

	public boolean contains( K key ) {
		return get( key ) != null;
	}
	public V get( K key ) {
		boolean[] marked = {false};
		int hash = getHash( key );
		Node curr = this.head;
		while( curr.hash < hash ) {
			curr = curr.next.getReference();
			Node succ = curr.next.get(marked);
		}
		return ( curr.hash == hash && !marked[0]) ? curr.value : null;
	}

	public boolean add( K key, V value ) {
		int hash = getHash( key );
		while( true ) {
			Window window = find( head, hash );
			Node pred = window.pred;
			Node curr = window.curr;
			if( curr.hash == hash ) {
				return false;
			} else {
				Node node = new Node( hash, key, value );
				node.next = new AtomicMarkableReference<BucketListMap<K, V>.Node>(curr,false);
				if(pred.next.compareAndSet(curr, node, false, false)) {
					return true;
				}
			}
		}
	}

	private Node addSentinel( int bucket ) {
		int hash = makeSentinelHash( bucket );
		boolean splice;
		while( true ) {
			Window window = find( head, hash );
			Node pred = window.pred;
			Node curr = window.curr;
			if( curr.hash == hash )
				return curr; // all sentinels are equal
			else {
				Node node = new Node( hash );
				node.next = new AtomicMarkableReference<Node>(curr, false);
				pred.next.compareAndSet(curr, node, false, false);
				return node; // return the newly created node
			}
		}
	}

	public boolean remove( K key ) {
		int hash = getHash( key );
		boolean snip;
		while( true ) {
			Window window = find( head, hash );
			Node pred = window.pred;
			Node curr = window.curr;
			if( curr.hash != hash )
				return false;
			else {
				// Unlink node
				Node succ = curr.next.getReference();
				snip = curr.next.compareAndSet(succ, succ, false, true);
				if(!snip)
					continue;
				pred.next.compareAndSet(curr, succ, false, false);
				return true;
			}
		}
	}

	/**
	 * Constructor
	 */
	public BucketListMap() {
		this.head = new Node( 0 );
		AtomicMarkableReference<Node> tail = new AtomicMarkableReference<Node>(new Node( Integer.MAX_VALUE ),false);
		this.head.next = tail;
		tail.getReference().next = new AtomicMarkableReference<Node>(null,false);
	}
	private BucketListMap(Node e) {
		this.head  = e;
	}
	/**
	 * Restricted-size hash code
	 * No need to modify this
	 * @param x object to hash
	 * @return hash code
	 */
	public static int hashCode(Object x) {
		return x.hashCode() & PRE_MASK;
	}
	private int getHash( K key ) {
		int code = hashCode( key );
		return reverse(code | REG_BIT);
	}
	private int makeSentinelHash(int key) {
		return reverse(key & PRE_MASK);
	}

	private static int reverse(int key) {
		int loMask = LO_BIT;
		int hiMask = REG_BIT;
		int result = 0;
		for (int i = 0; i <= WORD_SIZE; i++) {
			if ((key & loMask) != 0) {  // bit set
				result |= hiMask;
			}
			loMask <<= 1;
			hiMask >>>= 1;  // fill with 0 from left
		}
		return result;
	}
	public BucketListMap<K,V> getSentinel(int index) {
		Node node = addSentinel( index );
		if( node == null )
			return null;
		return new BucketListMap<K,V>( node );
	}

	public int debuggingCountElements() {
		int count = 0;
		Node curr = head.next.getReference();
		while ( curr != null ) {
			if( curr.value != null ) // is this a sentinel node?
				++count;
			curr = curr.next.getReference();
		}
		return count;
	}
}
