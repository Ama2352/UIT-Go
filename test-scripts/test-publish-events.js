/**
 * Minimal RabbitMQ event publisher for exercising the notification service.
 *
 * Usage:
 *   node test-publish-events.js <routing-key> <user-id>
 *
 * Examples:
 *   node test-publish-events.js trip.started aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
 *   node test-publish-events.js payment.success aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
 */

const amqp = require('amqplib');

const config = {
  url: process.env.RABBITMQ_URL || 'amqp://guest:guest@localhost:5672',
  exchange: process.env.RABBITMQ_EXCHANGE || 'trip_events',
};

const templates = {
  'trip.assigned': (userId) => ({
    tripId: uuid(),
    passengerId: userId,
    driverId: '11111111-1111-1111-1111-111111111111',
    driverName: 'John Driver',
    etaMinutes: 5,
  }),
  'trip.started': (userId) => ({
    tripId: uuid(),
    passengerId: userId,
    pickupAddress: '123 Main St',
    startTime: new Date().toISOString(),
  }),
  'trip.completed': (userId) => ({
    tripId: uuid(),
    passengerId: userId,
    fare: 50000,
    endTime: new Date().toISOString(),
  }),
  'trip.cancelled': (userId) => ({
    tripId: uuid(),
    passengerId: userId,
    cancelledBy: 'PASSENGER',
    cancelledAt: new Date().toISOString(),
  }),
  'payment.success': (userId) => ({
    paymentId: `pay-${uuid().slice(0, 8)}`,
    tripId: uuid(),
    passengerId: userId,
    amount: 50000,
    currency: 'VND',
    timestamp: new Date().toISOString(),
  }),
};

async function publishEvent(routingKey, userId) {
  const payload = buildPayload(routingKey, userId);
  let connection;

  try {
    connection = await amqp.connect(config.url);
    const channel = await connection.createChannel();
    await channel.assertExchange(config.exchange, 'topic', { durable: true });

    channel.publish(
      config.exchange,
      routingKey,
      Buffer.from(JSON.stringify(payload)),
      { persistent: true, contentType: 'application/json' },
    );

    console.log(`Published "${routingKey}" for ${userId}`);
    console.log(JSON.stringify(payload, null, 2));

    await channel.close();
  } finally {
    if (connection) {
      await connection.close();
    }
  }

  return payload;
}

function buildPayload(routingKey, userId) {
  const builder = templates[routingKey];
  if (!builder) {
    const available = Object.keys(templates).join(', ');
    throw new Error(`Unknown routing key "${routingKey}". Available: ${available}`);
  }

  if (!userId) {
    throw new Error('userId is required');
  }

  return builder(userId);
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function printUsage() {
  console.error('Usage: node test-publish-events.js <routing-key> <userId (get it from user-service)>');
  console.error('Available routing keys:');
  Object.keys(templates).forEach((key) => console.error(`  - ${key}`));
}

if (require.main === module) {
  const [routingKey, userId] = process.argv.slice(2);

  if (!routingKey || !userId) {
    printUsage();
    process.exit(1);
  }

  publishEvent(routingKey, userId).catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}

module.exports = { publishEvent, buildPayload, templates };

