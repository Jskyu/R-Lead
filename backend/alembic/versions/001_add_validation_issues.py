"""Add validation_issues field to leads

Revision ID: 001
Revises:
Create Date: 2024-01-15 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '001'
down_revision: Union[str, None] = '000'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Add validation_issues column to leads table
    op.add_column('leads', sa.Column('validation_issues', sa.JSON(), nullable=True))


def downgrade() -> None:
    # Remove validation_issues column from leads table
    op.drop_column('leads', 'validation_issues')
