import { useState } from 'react'
import axios from 'axios'
import './App.css'

function App() {
  const [productId, setProductId] = useState('P100')
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setResult(null)
    setError(null)
    try {
      const response = await axios.post('http://localhost:8080/api/orders', {
        productId,
        quantity: parseInt(quantity)
      })
      setResult(response.data)
    } catch (err) {
      if (err.response) {
        setResult(err.response.data)
      } else {
        setError(err.message)
      }
    }
  }

  return (
    <div className="App">
      <h1>Order System</h1>
      <form onSubmit={handleSubmit} style={{ marginBottom: '20px' }}>
        <div>
          <label>Product: </label>
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            <option value="P100">P100 - Wireless Mouse</option>
            <option value="P200">P200 - Mechanical Keyboard</option>
            <option value="P300">P300 - USB-C Hub</option>
          </select>
        </div>
        <div style={{ marginTop: '10px' }}>
          <label>Quantity: </label>
          <input 
            type="number" 
            value={quantity} 
            onChange={(e) => setQuantity(e.target.value)} 
            min="1" 
          />
        </div>
        <button type="submit" style={{ marginTop: '15px' }}>Place Order</button>
      </form>

      {error && <div style={{ color: 'red' }}>Error: {error}</div>}
      
      {result && (
        <div className="result-area" style={{ border: '1px solid #ccc', padding: '15px', borderRadius: '5px' }}>
          <h2>Result: {result.status}</h2>
          <p><strong>Reason:</strong> {result.reason}</p>
          {result.inventory && (
            <div>
              <h3>Inventory Details:</h3>
              <p>Product ID: {result.inventory.productId}</p>
              <p>Name: {result.inventory.name}</p>
              <p>Remaining Stock: {result.inventory.stock}</p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default App
