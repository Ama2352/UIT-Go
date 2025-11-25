export interface Notification {
  userId: string;
  type: string;
  title: string;
  message: string;
  payload?: any;
  createdAt: Date;
}

