import { Socket } from 'socket.io';

export interface WebSocketClient extends Socket {
  user: {
    userId: string;
    email: string;
    role: string;
  };
}

