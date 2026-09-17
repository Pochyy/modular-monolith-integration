import { useState, useEffect } from 'react'
import axios from 'axios'
import './App.css'

const API = 'http://localhost:8080/api'

function App() {
  const [cart, setCart] = useState([])
  const [selectedProduct, setSelectedProduct] = useState('P100')
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])
  const [lowStockThreshold] = useState(5)

  useEffect(() => {
    fetchInventory()
    fetchOrders()
    fetchNotifications()
  }, [])

  const fetchInventory = async () => {
    try {
      const res = await axios.get(`${API}/inventory`)
      setInventory(res.data)
    } catch (e) { console.error('Failed to fetch inventory', e) }
  }

  const fetchOrders = async () => {
    try {
      const res = await axios.get(`${API}/orders`)
      setOrders(res.data)
    } catch (e) { console.error('Failed to fetch orders', e) }
  }

  const fetchNotifications = async () => {
    try {
      const res = await axios.get(`${API}/notifications`)
      setNotifications(res.data)
    } catch (e) { console.error('Failed to fetch notifications', e) }
  }

  const refreshAll = () => {
    fetchInventory()
    fetchOrders()
    fetchNotifications()
  }

  const addToCart = () => {
    const existing = cart.find(item => item.productId === selectedProduct)
    if (existing) {
      setCart(cart.map(item =>
        item.productId === selectedProduct
          ? { ...item, quantity: item.quantity + parseInt(quantity) }
          : item
      ))
    } else {
      setCart([...cart, { productId: selectedProduct, quantity: parseInt(quantity) }])
    }
  }

  const removeFromCart = (productId) => {
    setCart(cart.filter(item => item.productId !== productId))
  }

  const updateCartQuantity = (productId, newQty) => {
    if (newQty <= 0) {
      removeFromCart(productId)
      return
    }
    setCart(cart.map(item =>
      item.productId === productId ? { ...item, quantity: newQty } : item
    ))
  }

  const submitOrder = async () => {
    setResult(null)
    setError(null)
    try {
      const response = await axios.post(`${API}/orders`, { items: cart })
      setResult(response.data)
      setCart([])
      refreshAll()
    } catch (err) {
      if (err.response) {
        setResult(err.response.data)
      } else {
        setError(err.message)
      }
    }
  }

  const cancelOrder = async (orderId) => {
    try {
      await axios.post(`${API}/orders/${orderId}/cancel`)
      refreshAll()
    } catch (err) {
      if (err.response) {
        alert(err.response.data.error || 'Cancel failed')
      } else {
        alert(err.message)
      }
    }
  }

  const getProductName = (productId) => {
    const item = inventory.find(p => p.productId === productId)
    return item ? item.name : productId
  }

  return (
    <div className="App" style={{ textAlign: 'left', maxWidth: '900px', margin: '0 auto', padding: '20px' }}>
      <h1 style={{ textAlign: 'center' }}>Order System</h1>

      {/* ---- Inventory Dashboard ---- */}
      <section style={{ marginBottom: '30px' }}>
        <h2>Inventory</h2>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #333' }}>
              <th style={{ padding: '8px', textAlign: 'left' }}>Product ID</th>
              <th style={{ padding: '8px', textAlign: 'left' }}>Name</th>
              <th style={{ padding: '8px', textAlign: 'right' }}>Stock</th>
            </tr>
          </thead>
          <tbody>
            {inventory.map(p => (
              <tr key={p.productId}
                  style={{
                    borderBottom: '1px solid #ddd',
                    backgroundColor: p.stock < lowStockThreshold ? '#ffe0e0' : 'transparent'
                  }}>
                <td style={{ padding: '8px' }}>{p.productId}</td>
                <td style={{ padding: '8px' }}>{p.name}</td>
                <td style={{ padding: '8px', textAlign: 'right' }}>
                  {p.stock}
                  {p.stock < lowStockThreshold && <span style={{ color: 'red', marginLeft: '8px' }}>⚠ Low</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <button onClick={fetchInventory} style={{ marginTop: '8px' }}>Refresh Inventory</button>
      </section>

      {/* ---- Cart / Place Order ---- */}
      <section style={{ marginBottom: '30px' }}>
        <h2>Place Order</h2>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', marginBottom: '10px' }}>
          <select value={selectedProduct} onChange={e => setSelectedProduct(e.target.value)}>
            {inventory.map(p => (
              <option key={p.productId} value={p.productId}>
                {p.productId} - {p.name}
              </option>
            ))}
            {inventory.length === 0 && (
              <>
                <option value="P100">P100 - Wireless Mouse</option>
                <option value="P200">P200 - Mechanical Keyboard</option>
                <option value="P300">P300 - USB-C Hub</option>
              </>
            )}
          </select>
          <input
            type="number"
            value={quantity}
            onChange={e => setQuantity(e.target.value)}
            min="1"
            style={{ width: '60px' }}
          />
          <button onClick={addToCart}>Add to Cart</button>
        </div>

        {cart.length > 0 && (
          <div>
            <h3>Cart</h3>
            <table style={{ width: '100%', borderCollapse: 'collapse', marginBottom: '10px' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #333' }}>
                  <th style={{ padding: '6px', textAlign: 'left' }}>Product</th>
                  <th style={{ padding: '6px', textAlign: 'right' }}>Qty</th>
                  <th style={{ padding: '6px', textAlign: 'center' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {cart.map(item => (
                  <tr key={item.productId} style={{ borderBottom: '1px solid #ddd' }}>
                    <td style={{ padding: '6px' }}>{item.productId} - {getProductName(item.productId)}</td>
                    <td style={{ padding: '6px', textAlign: 'right' }}>
                      <input
                        type="number"
                        value={item.quantity}
                        min="1"
                        style={{ width: '50px' }}
                        onChange={e => updateCartQuantity(item.productId, parseInt(e.target.value) || 0)}
                      />
                    </td>
                    <td style={{ padding: '6px', textAlign: 'center' }}>
                      <button onClick={() => removeFromCart(item.productId)}>Remove</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <button onClick={submitOrder} style={{ fontWeight: 'bold' }}>Submit Order</button>
          </div>
        )}
      </section>

      {/* ---- Order Result ---- */}
      {error && <div style={{ color: 'red', marginBottom: '20px' }}>Error: {error}</div>}
      {result && (
        <section style={{ border: '2px solid ' + (result.status === 'CONFIRMED' ? 'green' : 'red'),
                          padding: '15px', borderRadius: '5px', marginBottom: '30px' }}>
          <h2>Order Result: {result.status}</h2>
          <p><strong>Reason:</strong> {result.reason}</p>
          {result.items && (
            <div>
              <h3>Items:</h3>
              <ul>
                {result.items.map((item, i) => (
                  <li key={i}>{item.productId}: {item.outcome}</li>
                ))}
              </ul>
            </div>
          )}
          {result.inventory && (
            <div>
              <h3>Updated Inventory:</h3>
              <ul>
                {result.inventory.map((inv, i) => (
                  <li key={i}>{inv.productId} ({inv.name}): {inv.stock} remaining</li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      {/* ---- Order History ---- */}
      <section style={{ marginBottom: '30px' }}>
        <h2>Order History</h2>
        <button onClick={fetchOrders} style={{ marginBottom: '8px' }}>Refresh Orders</button>
        {orders.length === 0 && <p>No orders yet.</p>}
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #333' }}>
              <th style={{ padding: '6px', textAlign: 'left' }}>Order ID</th>
              <th style={{ padding: '6px', textAlign: 'left' }}>Status</th>
              <th style={{ padding: '6px', textAlign: 'left' }}>Reason</th>
              <th style={{ padding: '6px', textAlign: 'left' }}>Items</th>
              <th style={{ padding: '6px', textAlign: 'left' }}>Date</th>
              <th style={{ padding: '6px', textAlign: 'center' }}>Action</th>
            </tr>
          </thead>
          <tbody>
            {orders.map(order => (
              <tr key={order.orderId} style={{ borderBottom: '1px solid #ddd' }}>
                <td style={{ padding: '6px' }}>{order.orderId}</td>
                <td style={{ padding: '6px' }}>
                  <span style={{
                    color: order.status === 'CONFIRMED' ? 'green' : order.status === 'REJECTED' ? 'red' : 'gray',
                    fontWeight: 'bold'
                  }}>{order.status}</span>
                </td>
                <td style={{ padding: '6px' }}>{order.reason}</td>
                <td style={{ padding: '6px' }}>
                  {order.items && order.items.map((item, i) => (
                    <div key={i}>{item.productId} x{item.quantity}</div>
                  ))}
                </td>
                <td style={{ padding: '6px' }}>{order.createdAt ? new Date(order.createdAt).toLocaleString() : ''}</td>
                <td style={{ padding: '6px', textAlign: 'center' }}>
                  {order.status === 'CONFIRMED' && (
                    <button onClick={() => cancelOrder(order.orderId)}>Cancel</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {/* ---- Notifications ---- */}
      <section style={{ marginBottom: '30px' }}>
        <h2>Notifications</h2>
        <button onClick={fetchNotifications} style={{ marginBottom: '8px' }}>Refresh Notifications</button>
        {notifications.length === 0 && <p>No notifications yet.</p>}
        <ul>
          {notifications.map(n => (
            <li key={n.notificationId} style={{ marginBottom: '6px' }}>
              <strong>{n.createdAt ? new Date(n.createdAt).toLocaleString() : ''}</strong> — {n.message}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

export default App
