import { Module, Global } from '@nestjs/common';
import { PrismaService, PrismaReplicaService } from './prisma.service';

@Global()
@Module({
  providers: [PrismaService, PrismaReplicaService],
  exports: [PrismaService, PrismaReplicaService],
})
export class PrismaModule {}

